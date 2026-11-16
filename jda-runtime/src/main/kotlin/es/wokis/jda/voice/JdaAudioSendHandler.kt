package es.wokis.jda.voice

import es.wokis.player.engine.audio.ContinuousPlayerAudioStream
import es.wokis.player.engine.audio.PlayerAudioState
import es.wokis.voice.AudioFrame
import es.wokis.voice.AudioFrameFormat
import es.wokis.voice.AudioFrameSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import net.dv8tion.jda.api.audio.AudioSendHandler
import java.nio.ByteBuffer
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger

/**
 * JDA [AudioSendHandler] over a neutral [AudioFrameSource].
 *
 * [canProvide] may prefetch one frame; [provide20MsAudio] returns an array-backed copy and never
 * touches the source or a logger. The source is in-memory, so the audio thread is never blocked.
 * Frames are pre-encoded Opus, so [isOpus] is `true` and JDA performs no encoding.
 *
 * [close] is the explicit stop: after it returns, [canProvide] yields `false` and
 * [provide20MsAudio] yields `null`. A frame already claimed and materialised may still complete its
 * in-flight return, and a poll already in flight is discarded; neither confirms network delivery.
 * [close] does not close the caller-owned source. A stopped handler is not a completed playback, so
 * compare [framesProvided] with [AudioFrameSource.expectedFrameCount].
 *
 * Only the small in-memory state ([prefetched], [closed], [exhausted], the first [sourceFailure])
 * and the copy of a claimed frame are guarded by [stateLock]; source calls and completion callbacks
 * always run outside it.
 *
 * Fatal precedence: a source failure recorded before termination is published instead of
 * [StoppedException], and a fatal failure (`Error` or [CancellationException]) releases the waits
 * and is then rethrown rather than flattened into an ordinary result.
 */
internal class JdaAudioSendHandler private constructor(
    private val supply: FrameSupply,
    /**
     * Terminal signal published when playback completes, is stopped or fails. The injectable seam
     * lets a test park a failure notification and force the close-versus-failure ordering; it is
     * only ever completed outside [stateLock].
     */
    private val completion: CompletableFuture<Unit>,
) : AudioSendHandler, AutoCloseable {

    /** Finite Phase 2C path over a caller-owned [AudioFrameSource]; the caller closes it. */
    internal constructor(
        source: AudioFrameSource,
        completion: CompletableFuture<Unit> = CompletableFuture(),
    ) : this(FrameSupply.from(source), completion)

    init {
        require(supply.format == AudioFrameFormat.OPUS_20MS) {
            "The JDA voice adapter only accepts ${AudioFrameFormat.OPUS_20MS} frames"
        }
    }

    /**
     * Guards [prefetched], [exhausted], [closed], the first [sourceFailure] and the claimed-frame
     * copy, so [close], the claim in [canProvide] and [provide20MsAudio] linearize. No source call,
     * suspension, logging or completion callback runs while it is held.
     */
    private val stateLock = Any()

    private var prefetched: AudioFrame? = null

    private var exhausted = false

    private var closed = false

    private val provided = AtomicInteger(0)

    /**
     * First source failure, recorded under [stateLock] before the future is failed outside it, so
     * [close] either sees it or a later recording loses. Never read without the monitor.
     */
    private var sourceFailure: Throwable? = null

    /** True when the underlying source failed instead of simply ending. */
    val failed: Boolean get() = synchronized(stateLock) { sourceFailure != null }

    /** Frames returned to JDA's send loop so far. */
    val framesProvided: Int get() = provided.get()

    /**
     * Claims one frame for the next send cycle.
     *
     * The closed/exhausted check and the claim run under [stateLock]; the single non-blocking
     * [AudioFrameSource.nextFrame] poll runs outside it. A poll already in flight when [close]
     * linearizes is discarded. A recoverable [Exception] is recorded and reported as `false`; a
     * fatal failure is recorded, then rethrown.
     */
    override fun canProvide(): Boolean {
        val earlyResult: Boolean? = synchronized(stateLock) {
            when {
                closed || exhausted -> false
                prefetched != null -> true
                else -> null
            }
        }
        if (earlyResult != null) return earlyResult

        var sourceFailed = false
        val frame = try {
            supply.nextFrame()
        } catch (cancellation: CancellationException) {
            // Wake the waiter before the fatal failure escapes the audio thread.
            recordSourceFailure(cancellation)
            throw cancellation
        } catch (error: Error) {
            recordSourceFailure(error)
            throw error
        } catch (error: Exception) {
            recordSourceFailure(error)
            sourceFailed = true
            null
        } catch (error: Throwable) {
            // Not recoverable either: signal and rethrow instead of pretending the source ended.
            recordSourceFailure(error)
            throw error
        }

        if (frame == null) {
            if (!sourceFailed) recordExhaustion()
            return false
        }

        return synchronized(stateLock) {
            if (closed) {
                // close() won the race; the frame must not escape to the send loop.
                false
            } else {
                prefetched = frame
                true
            }
        }
    }

    /**
     * Hands the prefetched frame to JDA's send loop as an independent array-backed buffer.
     *
     * Claim and copy run under [stateLock], the same monitor [close] uses; a call that enters after
     * [close] sees `closed` and returns `null` without touching the source.
     */
    override fun provide20MsAudio(): ByteBuffer? {
        val buffer = synchronized(stateLock) {
            val pending = prefetched
            prefetched = null
            if (closed || pending == null) null else pending.payloadBuffer()
        } ?: return null

        val count = provided.incrementAndGet()
        if (count >= supply.expectedFrameCount) {
            completion.complete(Unit)
        }
        return buffer
    }

    /** The frames are pre-encoded Opus, so JDA must not encode them again. */
    override fun isOpus(): Boolean = true

    /**
     * Stops this handler without touching the caller-owned [source].
     *
     * Idempotent. Under [stateLock] it marks the handler closed, drops a prefetched frame and reads
     * the recorded failure; no new claim starts. A failure recorded before this point is published
     * instead of [StoppedException], and the completion itself still runs outside the lock. Waiters
     * are released as a stop, not as a success.
     */
    override fun close() {
        val recordedFailure = synchronized(stateLock) {
            if (closed) {
                return
            }
            closed = true
            prefetched = null
            // Read together with `closed`; a failure recorded before this point is published below.
            sourceFailure
        }
        completion.completeExceptionally(recordedFailure ?: StoppedException())
    }

    /** Distinguishes an explicit [close] from a completed or exhausted playback. */
    private class StoppedException : IllegalStateException(
        "The JDA audio send handler was closed before the playback completed",
    )

    /**
     * Records the first source failure under [stateLock], then fails the future outside it.
     *
     * The record and the terminal transition are decided together, so a concurrent [close] sees the
     * failure; a recording that loses to an earlier close or exhaustion changes nothing.
     */
    private fun recordSourceFailure(error: Throwable) {
        val firstFailure = synchronized(stateLock) {
            if (closed || exhausted) {
                null
            } else {
                sourceFailure = error
                exhausted = true
                error
            }
        } ?: return
        completion.completeExceptionally(firstFailure)
    }

    private fun recordExhaustion() {
        val firstExhaustion = synchronized(stateLock) {
            if (closed || exhausted) {
                false
            } else {
                exhausted = true
                true
            }
        }
        if (firstExhaustion) {
            completion.complete(Unit)
        }
    }

    /**
     * Blocking wait for non-coroutine callers and tests; coroutines must use
     * [awaitCompletionCancellable].
     *
     * Returns `false` for a timeout, an interrupted wait, an explicit [close] or a recoverable
     * source [Exception]; a fatal failure is rethrown, matching [awaitCompletionCancellable].
     */
    fun awaitCompletion(timeoutMillis: Long): Boolean = try {
        completion.get(timeoutMillis, TimeUnit.MILLISECONDS)
        true
    } catch (interrupted: InterruptedException) {
        // Restore the interrupt flag and report an unfinished playback.
        Thread.currentThread().interrupt()
        false
    } catch (_: TimeoutException) {
        false
    } catch (execution: ExecutionException) {
        rethrowFatalOrReportIncomplete(execution.unwrapCompletionFailure())
    } catch (completionFailure: CompletionException) {
        rethrowFatalOrReportIncomplete(completionFailure.unwrapCompletionFailure())
    } catch (cancellation: CancellationException) {
        // A recorded fatal failure outranks the generic future cancellation.
        throw (recordedFatalFailure() ?: cancellation)
    } catch (_: Exception) {
        // A stop is not a completion; a recorded fatal failure keeps precedence.
        recordedFatalFailure()?.let { throw it }
        false
    }

    /**
     * Unwraps the `ExecutionException`/`CompletionException` envelope so classification sees the
     * recorded cause; a wrapper without a cause stays an ordinary failure, never a clean exhaustion.
     */
    private fun Throwable.unwrapCompletionFailure(): Throwable = when (this) {
        is ExecutionException -> cause ?: this
        is CompletionException -> cause ?: this
        else -> this
    }

    /**
     * Rethrows a fatal failure with the same precedence as [canProvide]; only a recoverable
     * [Exception] becomes `false`. The wrapper can hide [StoppedException] next to a recorded fatal
     * failure, so any fatal already recorded under [stateLock] still wins.
     */
    private fun rethrowFatalOrReportIncomplete(failure: Throwable): Boolean {
        when (failure) {
            is CancellationException -> throw failure
            is Error -> throw failure
        }
        recordedFatalFailure()?.let { throwFatal(it) }
        return false
    }

    private fun throwFatal(failure: Throwable): Nothing = throw failure

    /**
     * Returns a recorded fatal source failure (`Error` or [CancellationException]), or `null`.
     * Read under [stateLock] so a later stop cannot hide it.
     */
    private fun recordedFatalFailure(): Throwable? = synchronized(stateLock) {
        when (val failure = sourceFailure) {
            is CancellationException -> failure
            is Error -> failure
            else -> null
        }
    }

    /**
     * Cancellable wait for the send loop to consume the expected frame count.
     *
     * Cancellation interrupts the wait immediately, so cleanup need not wait for a timeout. A
     * recoverable failure or timeout returns `false`; a fatal failure releases the wait and is then
     * rethrown so the session port keeps its precedence and still runs cleanup.
     */
    suspend fun awaitCompletionCancellable(timeoutMillis: Long): Boolean = try {
        when {
            completion.isDone -> {
                completion.awaitCancellable()
                true
            }

            timeoutMillis <= 0 -> false

            else -> withTimeoutOrNull(timeoutMillis) {
                completion.awaitCancellable()
                true
            } ?: false
        }
    } catch (cancellation: CancellationException) {
        // Both source and coroutine cancellation are fatal; a recorded fatal failure outranks the
        // generic completion cancellation.
        throw (recordedFatalFailure() ?: cancellation)
    } catch (error: Error) {
        throw error
    } catch (_: Exception) {
        // A stop is not a completion; a recorded fatal failure keeps precedence.
        recordedFatalFailure()?.let { throw it }
        false
    }

    internal companion object {
        /**
         * Phase 3 continuous stream over the same handler.
         *
         * Paused playback maps to a valid 20 ms Opus silence frame, never `null`/EOF, so the send
         * loop keeps running and Lavaplayer keeps receiving `provide(0)` liveness polls. Only
         * [PlayerAudioState.Ended] is terminal.
         */
        fun continuous(stream: ContinuousPlayerAudioStream): JdaAudioSendHandler =
            JdaAudioSendHandler(ContinuousFrameSupply(stream), CompletableFuture())
    }
}

/**
 * Non-blocking frame supply behind [JdaAudioSendHandler]; lets the finite and continuous paths
 * share the same claim/copy/close machinery.
 */
private interface FrameSupply {
    val format: AudioFrameFormat
    val expectedFrameCount: Int
    fun nextFrame(): AudioFrame?

    companion object {
        fun from(source: AudioFrameSource): FrameSupply = AudioFrameSourceSupply(source)
    }
}

private class AudioFrameSourceSupply(
    private val source: AudioFrameSource,
) : FrameSupply {
    override val format: AudioFrameFormat get() = source.format
    override val expectedFrameCount: Int get() = source.expectedFrameCount
    override fun nextFrame(): AudioFrame? = source.nextFrame()
}

private class ContinuousFrameSupply(
    private val stream: ContinuousPlayerAudioStream,
) : FrameSupply {
    override val format: AudioFrameFormat = AudioFrameFormat.OPUS_20MS
    override val expectedFrameCount: Int = Int.MAX_VALUE

    override fun nextFrame(): AudioFrame? = when (val state = stream.next()) {
        is PlayerAudioState.Frame -> state.frame
        PlayerAudioState.PausedSilence -> OPUS_SILENCE_FRAME
        PlayerAudioState.Ended -> null
    }
}

/** A valid 20 ms mono Opus packet carrying silence (same payload Lavaplayer uses). */
private val OPUS_SILENCE_FRAME: AudioFrame = AudioFrame.of(
    AudioFrameFormat.OPUS_20MS,
    byteArrayOf(0xFC.toByte(), 0xFF.toByte(), 0xFE.toByte()),
)
