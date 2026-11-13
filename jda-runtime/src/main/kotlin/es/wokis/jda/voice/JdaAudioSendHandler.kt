package es.wokis.jda.voice

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
 * [canProvide] may prefetch exactly one frame; [provide20MsAudio] returns an array-backed
 * copy of that frame once and never touches the source, disk or a logger. The source itself is
 * in-memory, so the audio send thread is never blocked; copying a bounded Opus packet is a
 * small, non-blocking allocation. Frames are pre-encoded Opus packets, hence [isOpus] is `true`
 * and JDA performs no encoding.
 *
 * Lifecycle: JDA's audio thread calls [canProvide] and then [provide20MsAudio] on every send
 * tick. [close] is the explicit stop. It linearizes with the in-memory claim and buffer-copy
 * section under [stateLock]: once that section has run, it marks the handler closed, prevents
 * any new frame claim, drops a frame that was prefetched but not yet claimed, and releases
 * waiters. After [close] returns, every subsequent [canProvide] returns `false` and every
 * subsequent [provide20MsAudio] returns `null`, and neither starts a new source poll or claim.
 * A [canProvide] that was already past the lock and polling the source when [close] linearized
 * may finish that non-blocking poll after [close] returns, but it discards the result: that
 * frame is never stored and never handed to JDA. A frame claimed under the lock, with its buffer
 * already materialized before [close] linearized, may still complete its already-in-flight method
 * return after [close] returns, because that return is the hand-off to JDA's send loop. [close]
 * therefore cannot cancel a packet already handed to JDA or stop the network layer from
 * sending it, and it cannot confirm or deny network delivery. Session cleanup then detaches
 * this handler and closes the voice connection. JDA tolerates `false` and `null`, so a close
 * racing with the send loop is safe. The handler does *not* close the [AudioFrameSource]: the
 * caller owns it and already closes it on every exit path. A stopped handler is not a completed
 * playback: waiters wake with `false` and no new frame is claimed after [close], so a caller that
 * requires the full playback compares [framesProvided] with
 * [AudioFrameSource.expectedFrameCount] rather than trusting a wake-up alone. The hand-off counter
 * is not frozen by [close]: a frame that was claimed and materialized before [close] linearized may
 * still complete its already-in-flight return after [close] returns, and that return increments
 * [framesProvided] at that moment. Such an increment is still only a hand-off to JDA's send loop,
 * not network delivery.
 *
 * Only the tiny in-memory state ([prefetched], [closed], [exhausted], the first recorded source
 * failure) and the copy of the claimed frame into its independent array-backed buffer are
 * synchronized. Source calls and [CompletableFuture] completion callbacks always run outside
 * the lock, so JDA's audio thread cannot wait behind I/O, logging or listener code; the lock
 * only ever covers a few field reads and one bounded Opus-size array copy.
 *
 * The first source failure and the termination state are decided under that same monitor, so
 * a [close] racing a source failure cannot downgrade an already-recorded failure into an
 * ordinary stop: the transition that wins the monitor decides the outcome, and the recorded
 * failure is what completes the future outside the lock. Because the failure is stored before
 * its notification is attempted, the outcome does not depend on which thread reaches the
 * future first: a [close] that linearizes after the recording reads the same failure and
 * publishes it instead of `StoppedException`, and the later of the two [CompletableFuture]
 * calls is simply a no-op. The injectable [completion] signal exists so a test can park that
 * notification and assert exactly this ordering.
 *
 * [framesProvided] counts frames handed to JDA's send loop, not confirmed network delivery.
 * [awaitCompletionCancellable] returns when the expected frame count was provided, when the
 * source is exhausted or when the source fails, and reports `false` when the handler is
 * closed early, the wait times out or the source fails with a recoverable `Exception`; a fatal
 * source failure (`Error` or [CancellationException]) releases the wait and is then rethrown so
 * it keeps its precedence instead of becoming an ordinary result. [awaitCompletion] is its
 * blocking counterpart with the same failure classification: it unwraps the recorded cause and
 * rethrows a fatal `Error` or [CancellationException], while only a timeout, an interruption, an
 * explicit [close] or a recoverable source `Exception` becomes an ordinary `false`.
 */
internal class JdaAudioSendHandler(
    private val source: AudioFrameSource,
    /**
     * Terminal signal published when the playback completes, is stopped or fails.
     *
     * Every production call site uses the default, freshly created future. The parameter exists
     * only as a narrow internal seam: a test can inject a [CompletableFuture] whose
     * [CompletableFuture.completeExceptionally] parks its caller, which holds the failure
     * notification after the first [sourceFailure] was already recorded under [stateLock]. That
     * turns the close-versus-failure race into a deterministic ordering instead of a timing
     * accident, without adding an application-level abstraction. This handler only ever calls
     * [CompletableFuture.complete] and [CompletableFuture.completeExceptionally] on it, and never
     * while [stateLock] is held.
     */
    private val completion: CompletableFuture<Unit> = CompletableFuture(),
) : AudioSendHandler, AutoCloseable {

    init {
        require(source.format == AudioFrameFormat.OPUS_20MS) {
            "The JDA voice adapter only accepts ${AudioFrameFormat.OPUS_20MS} frames"
        }
    }

    /**
     * Guards [prefetched], [exhausted], [closed], the first [sourceFailure] and the
     * materialisation of the claimed frame's buffer, so [close], [canProvide]'s claim,
     * [recordSourceFailure] and [provide20MsAudio] linearize against each other. No source call,
     * suspension, logging or completion callback is ever made while this monitor is held; the
     * only work inside it is a few field reads and one bounded array copy.
     */
    private val stateLock = Any()

    private var prefetched: AudioFrame? = null

    private var exhausted = false

    private var closed = false

    private val provided = AtomicInteger(0)

    /**
     * The first source failure, recorded under [stateLock] before the completion future is
     * failed outside it. Keeping it under the same monitor as [closed] and [exhausted] makes
     * failure/termination precedence atomic: [close] either linearizes before the failure and
     * reports a stop, or observes the recorded failure and completes the future with it instead
     * of downgrading it to a stop. Never read without the monitor.
     */
    private var sourceFailure: Throwable? = null

    /** True when the underlying source failed instead of simply ending. */
    val failed: Boolean get() = synchronized(stateLock) { sourceFailure != null }

    /** Frames returned to JDA's send loop so far. */
    val framesProvided: Int get() = provided.get()

    /**
     * Reports whether one frame can be claimed for the next send cycle.
     *
     * The closed/exhausted check and the claim itself are made under [stateLock]. When no frame
     * is prefetched, the single [AudioFrameSource.nextFrame] poll runs **outside** the lock
     * because it is non-blocking and performs no I/O; no source call, logging or completion
     * callback ever runs while the monitor is held. A poll already in flight when [close]
     * linearizes may finish after [close] returns, but its frame is discarded: it is never
     * stored as [prefetched] and never handed to JDA. A recoverable source [Exception] is
     * recorded and reported as `false`; a fatal failure ([Error] or [CancellationException]) is
     * recorded so the waiter wakes and is then rethrown.
     */
    override fun canProvide(): Boolean {
        synchronized(stateLock) {
            if (closed || exhausted) {
                return false
            }
            if (prefetched != null) {
                return true
            }
        }

        val frame = try {
            source.nextFrame()
        } catch (cancellation: CancellationException) {
            // Signalling the failure first wakes the session waiter, so cleanup can start even
            // though this fatal failure is about to escape the audio thread.
            recordSourceFailure(cancellation)
            throw cancellation
        } catch (error: Error) {
            // An Error must never be downgraded to a normal source failure: record it so the
            // waiter wakes, then let it propagate to the session port's fatal path.
            recordSourceFailure(error)
            throw error
        } catch (error: Exception) {
            recordSourceFailure(error)
            return false
        } catch (error: Throwable) {
            // Not an Exception or an Error, so it is not recoverable either: signal it and
            // rethrow instead of pretending the source only ended.
            recordSourceFailure(error)
            throw error
        }

        if (frame == null) {
            recordExhaustion()
            return false
        }

        return synchronized(stateLock) {
            if (closed) {
                // close() won the race after the source call: the frame dies with the handler
                // instead of escaping to the gateway send loop.
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
     * Claiming the frame and copying its payload into the returned buffer both happen inside
     * [stateLock], the same monitor [close] uses. A call that enters that section before [close]
     * has finished its copy before [close] can return; it may still complete its already-in-flight
     * method return after [close] returns, because that return is the hand-off to JDA. A call that
     * enters the section after [close] sees `closed` and returns `null` without touching the
     * source.
     */
    override fun provide20MsAudio(): ByteBuffer? {
        val buffer = synchronized(stateLock) {
            val pending = prefetched
            prefetched = null
            if (closed || pending == null) null else pending.payloadBuffer()
        } ?: return null

        val count = provided.incrementAndGet()
        if (count >= source.expectedFrameCount) {
            completion.complete(Unit)
        }
        return buffer
    }

    /** The frames are pre-encoded Opus, so JDA must not encode them again. */
    override fun isOpus(): Boolean = true

    /**
     * Stops this handler without touching the caller-owned [source].
     *
     * Idempotent. It linearizes with the in-memory claim and buffer-copy section under
     * [stateLock]: once that section has run, no new frame can be claimed, a frame prefetched at
     * that moment is discarded, and every subsequent [canProvide] returns `false` while every
     * subsequent [provide20MsAudio] returns `null`, neither starting a new source poll nor
     * touching the source. A [canProvide] that was already polling the source when this close
     * linearized may finish that non-blocking poll after [close] returns, but it discards the
     * result. A [provide20MsAudio] that claimed and materialized its buffer before this close
     * linearized may still complete its already-in-flight method return after [close] returns,
     * handing JDA the buffer it prepared. This close therefore cannot cancel a packet already
     * handed to JDA or stop the network layer from sending it, and it does not confirm or deny
     * network delivery: the contract covers only what the handler hands to JDA's send loop.
     * Session cleanup afterwards detaches the handler and closes the voice connection. Waiters
     * are released as a stop, not as a success: [awaitCompletion] and [awaitCompletionCancellable]
     * return `false` if the playback had not finished yet.
     *
     * When a source failure was already recorded under [stateLock], it takes precedence over the
     * stop: the future is completed with that recorded failure instead of [StoppedException], so
     * a close racing a source failure cannot downgrade a fatal failure that had already been
     * signalled. The recorded failure is read in the same critical section that publishes
     * [closed], so a concurrent recording either linearizes before this close and is published,
     * or linearizes after it and is rejected; the two outcomes cannot be interleaved. The
     * completion itself still runs outside the lock.
     */
    override fun close() {
        val recordedFailure = synchronized(stateLock) {
            if (closed) {
                return
            }
            closed = true
            prefetched = null
            // Read under the lock together with `closed`: a failure recorded before this point
            // is the one published below, regardless of which thread reaches the future first.
            sourceFailure
        }
        completion.completeExceptionally(recordedFailure ?: StoppedException())
    }

    /** Distinguishes an explicit [close] from a completed or exhausted playback. */
    private class StoppedException : IllegalStateException(
        "The JDA audio send handler was closed before the playback completed",
    )

    /**
     * Records the first source failure under [stateLock] and then fails the completion future
     * outside it with exactly that recorded instance.
     *
     * The store into [sourceFailure] and the terminal transition (`exhausted`) are decided
     * together under the monitor, before any notification is attempted, so the failure is visible
     * to a concurrent [close]: close observes it and completes with it rather than with
     * [StoppedException], even when the notification below has not been delivered yet. A failure
     * that loses the monitor to an earlier [close] or exhaustion changes no state and completes
     * nothing, so termination precedence is decided atomically by whichever transition runs
     * first. The notification is always issued outside the lock, which is why the ordering above
     * rests on the recorded state instead of on which thread calls [completion] first.
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
     * Blocking wait kept for non-coroutine callers and focused tests.
     *
     * Production coroutine paths must use [awaitCompletionCancellable] instead.
     *
     * Returns `true` when the completion signal arrived in time. A timeout, an interrupted wait,
     * an explicit [close] or a recoverable source `Exception` returns `false` so the caller can
     * report an incomplete playback and clean up. The completion signal is unwrapped and
     * classified exactly like in [awaitCompletionCancellable]: a fatal source failure (`Error` or
     * [CancellationException]) is rethrown instead of being flattened into an ordinary result, so
     * a blocking caller keeps the fatal precedence that [canProvide] already gave it.
     */
    fun awaitCompletion(timeoutMillis: Long): Boolean = try {
        completion.get(timeoutMillis, TimeUnit.MILLISECONDS)
        true
    } catch (interrupted: InterruptedException) {
        // Keep the blocking interrupt contract: restore the flag and report an unfinished
        // playback, so a caller that honours interruption still sees a non-fatal result.
        Thread.currentThread().interrupt()
        false
    } catch (_: TimeoutException) {
        false
    } catch (execution: ExecutionException) {
        rethrowFatalOrReportIncomplete(execution.unwrapCompletionFailure())
    } catch (completionFailure: CompletionException) {
        rethrowFatalOrReportIncomplete(completionFailure.unwrapCompletionFailure())
    } catch (cancellation: CancellationException) {
        // Cancelling the future through [awaitCancellable]'s cancellation hook surfaces here
        // directly; like a source-signalled cancellation, it is fatal and keeps its precedence.
        // A fatal source failure that was already recorded under the monitor outranks the generic
        // future cancellation, so it is rethrown instead of being masked by it.
        throw (recordedFatalFailure() ?: cancellation)
    } catch (_: Exception) {
        // A stopped or otherwise failed future is not a completion: a fatal source failure that
        // was recorded before the termination keeps its precedence over the ordinary stop.
        recordedFatalFailure()?.let { throw it }
        false
    }

    /**
     * Unwraps the wrapper [CompletableFuture] adds around the throwable passed to
     * [CompletableFuture.completeExceptionally], so classification sees the recorded cause rather
     * than its `ExecutionException`/`CompletionException` envelope. A wrapper without a cause is
     * kept as-is and therefore treated as an ordinary failure, never as a clean exhaustion.
     */
    private fun Throwable.unwrapCompletionFailure(): Throwable = when (this) {
        is ExecutionException -> cause ?: this
        is CompletionException -> cause ?: this
        else -> this
    }

    /**
     * Rethrows a fatal failure so it keeps the same precedence as in [canProvide], and reports
     * only a recoverable [Exception] as an ordinary incomplete playback.
     *
     * An `ExecutionException`/`CompletionException` can wrap [StoppedException] when [close]
     * linearized concurrently with a source failure, so a non-fatal wrapper is not enough to
     * conclude that the playback merely stopped: any fatal source failure already recorded under
     * [stateLock] is rethrown instead of being downgraded to `false`.
     */
    private fun rethrowFatalOrReportIncomplete(failure: Throwable): Boolean {
        when (failure) {
            is CancellationException -> throw failure
            is Error -> throw failure
        }
        recordedFatalFailure()?.let { throw it }
        return false
    }

    /**
     * Returns the recorded source failure when it is fatal, or `null`.
     *
     * Read under [stateLock], the same monitor that decides termination and records the failure,
     * so a waiter that observes a stopped or cancelled completion still recovers a fatal failure
     * that was recorded before the termination won the monitor. A recoverable `Exception` is
     * never returned: it keeps its ordinary `false` result.
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
     * The coroutine is resumed as soon as the completion signal arrives and cancellation
     * interrupts the wait immediately, so playback cleanup does not wait for the whole tone
     * timeout. A recoverable source failure or a timeout returns `false`; a fatal source failure
     * (`Error` or a source-signalled [CancellationException]) is rethrown after it has released
     * this wait, so the session port can preserve its fatal precedence and still run cleanup.
     */
    suspend fun awaitCompletionCancellable(timeoutMillis: Long): Boolean {
        return try {
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
            // A source may fail with a CancellationException of its own and an actual coroutine
            // cancellation reaches this catch too; both are fatal and must propagate instead of
            // being reported as an ordinary incomplete playback. A fatal failure recorded under
            // [stateLock] outranks the generic cancellation of the completion future, so it is
            // rethrown rather than masked.
            throw (recordedFatalFailure() ?: cancellation)
        } catch (error: Error) {
            throw error
        } catch (_: Exception) {
            // A stopped or otherwise failed future is not a completion: a fatal source failure
            // recorded before the termination keeps its precedence over the ordinary stop.
            recordedFatalFailure()?.let { throw it }
            false
        }
    }
}
