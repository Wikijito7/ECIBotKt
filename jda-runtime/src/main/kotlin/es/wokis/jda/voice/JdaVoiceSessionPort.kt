package es.wokis.jda.voice

import es.wokis.commands.neutral.CommandContext
import es.wokis.domain.model.GuildId
import es.wokis.domain.model.UserId
import es.wokis.voice.AudioFrameSource
import es.wokis.voice.VoiceFailureReason
import es.wokis.voice.VoicePlaybackResult
import es.wokis.voice.VoiceRequestRejection
import es.wokis.voice.VoiceSessionPort
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.audio.AudioSendHandler
import net.dv8tion.jda.api.audio.hooks.ConnectionListener
import net.dv8tion.jda.api.audio.hooks.ConnectionStatus
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.channel.concrete.StageChannel
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException
import net.dv8tion.jda.api.managers.AudioManager
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * JDA implementation of the neutral [VoiceSessionPort].
 *
 * The port resolves the invoking user's cached voice state, connects the bot to their
 * regular [VoiceChannel], sends the supplied pre-encoded Opus frames through a
 * [JdaAudioSendHandler] and attempts a bounded cleanup whose goal is to restore the
 * [AudioManager] state. Restoration is best-effort: failures are surfaced as
 * [VoiceFailureReason.CLEANUP_FAILED] or [VoiceFailureReason.DISCONNECT_UNCONFIRMED] instead of
 * being reported as a clean success, while a fatal cleanup failure (`Error` or cancellation) is
 * rethrown rather than converted into an ordinary result. No gateway identifier is logged or
 * returned in a user-facing message.
 *
 * At most one playback runs at a time. The connect and playback waits are coroutine-cancellable,
 * so cancellation resumes them immediately and starts cleanup instead of waiting for the
 * connect or tone timeout. Cleanup is attempted under `NonCancellable` with bounded waits, so
 * cancellation cannot skip the attempt; each cleanup step is best-effort and its failure is
 * recorded and surfaced. Cleanup first closes the active [JdaAudioSendHandler],
 * which invalidates it and drops any prefetched frame, and only then detaches it from the
 * [AudioManager]; the handler never closes the caller-owned source.
 *
 * An [AudioManager] that already owns a connected or connecting session in the target guild is
 * never moved, replaced or closed: the request is rejected with
 * [VoiceRequestRejection.VOICE_SESSION_ALREADY_ACTIVE] before any manager state is mutated.
 */
internal class JdaVoiceSessionPort(
    private val jda: JDA,
    private val connectTimeoutMillis: Long = DEFAULT_CONNECT_TIMEOUT_MILLIS,
    private val daveSettleMillis: Long = DEFAULT_DAVE_SETTLE_MILLIS,
    private val toneTimeoutMillis: Long = DEFAULT_TONE_TIMEOUT_MILLIS,
    private val flushMillis: Long = DEFAULT_FLUSH_MILLIS,
    private val disconnectTimeoutMillis: Long = DEFAULT_DISCONNECT_TIMEOUT_MILLIS,
    private val selfVoiceStatePollMillis: Long = DEFAULT_SELF_VOICE_STATE_POLL_MILLIS,
) : VoiceSessionPort {

    private val active = AtomicBoolean(false)

    override suspend fun play(context: CommandContext, source: AudioFrameSource): VoicePlaybackResult {
        if (!active.compareAndSet(false, true)) {
            return VoicePlaybackResult.Rejected(VoiceRequestRejection.PLAYBACK_IN_PROGRESS)
        }

        try {
            return when (val target = resolveTarget(context)) {
                is Target.Rejected -> VoicePlaybackResult.Rejected(target.reason)

                is Target.Ready -> withContext(Dispatchers.IO) {
                    connectAndPlay(target.guild, target.channel, source)
                }
            }
        } finally {
            active.set(false)
        }
    }

    private fun resolveTarget(context: CommandContext): Target {
        val guildId = context.guildId ?: return Target.Rejected(VoiceRequestRejection.DIRECT_MESSAGE)
        val guild = jda.getGuildById(guildId.toJdaId())
            ?: return Target.Rejected(VoiceRequestRejection.OTHER_GUILD)
        val member = guild.getMemberById(context.userId.toJdaId())
            ?: return Target.Rejected(VoiceRequestRejection.USER_NOT_IN_VOICE)
        val channel = member.voiceState?.channel
            ?: return Target.Rejected(VoiceRequestRejection.USER_NOT_IN_VOICE)

        val voiceChannel = when (channel) {
            is StageChannel -> return Target.Rejected(VoiceRequestRejection.STAGE_CHANNEL)
            is VoiceChannel -> channel
            else -> return Target.Rejected(VoiceRequestRejection.UNSUPPORTED_CHANNEL)
        }
        if (voiceChannel.guild.idLong != guild.idLong) {
            return Target.Rejected(VoiceRequestRejection.OTHER_GUILD)
        }

        return Target.Ready(guild, voiceChannel)
    }

    private suspend fun connectAndPlay(
        guild: Guild,
        channel: VoiceChannel,
        source: AudioFrameSource,
    ): VoicePlaybackResult {
        val audioManager = guild.audioManager
        // Detect the existing session before touching any manager state: opening a connection
        // would move it, and restoring state afterwards would close or replace it.
        if (audioManager.hasActiveSession()) {
            LOG.info("A voice session is already active; refusing to move, replace or close it")
            return VoicePlaybackResult.Rejected(VoiceRequestRejection.VOICE_SESSION_ALREADY_ACTIVE)
        }

        val previousConnectTimeout = audioManager.connectTimeout
        val previousAutoReconnect = audioManager.isAutoReconnect
        val previousListener = audioManager.connectionListener
        val previousSendingHandler = audioManager.sendingHandler
        val waiter = VoiceConnectionWaiter()
        var sendHandler: JdaAudioSendHandler? = null
        var outcome: VoicePlaybackResult = VoicePlaybackResult.Failed(VoiceFailureReason.INTERNAL_ERROR)
        var primaryFailure: Throwable? = null

        try {
            audioManager.setConnectionListener(waiter)
            audioManager.setAutoReconnect(false)
            audioManager.setConnectTimeout(connectTimeoutMillis)

            LOG.info("Opening the diagnostic voice connection")
            waiter.markRequested()
            audioManager.openAudioConnection(channel)

            waiter.awaitConnectedCancellable(connectTimeoutMillis)
            waiter.checkTerminal()
            LOG.info("Diagnostic voice connection established")

            delay(daveSettleMillis)
            waiter.checkTerminal()

            val handler = JdaAudioSendHandler(source)
            sendHandler = handler
            audioManager.setSendingHandler(handler)
            waiter.markPlaying()

            when (val signal = awaitPlaybackSignal(handler, waiter, toneTimeoutMillis)) {
                is PlaybackSignal.Terminal -> throw VoiceConnectionException(signal.reason)
                is PlaybackSignal.Frames -> outcome = framesOutcome(signal, handler, waiter, source)
            }
        } catch (cancellation: CancellationException) {
            primaryFailure = cancellation
            throw cancellation
        } catch (error: Error) {
            // A fatal playback/connect failure is rethrown, but recorded so cleanup can attach
            // its own failures to it instead of replacing it.
            primaryFailure = error
            throw error
        } catch (error: InsufficientPermissionException) {
            LOG.warn("The bot is missing a voice permission")
            primaryFailure = error
            outcome = VoicePlaybackResult.Failed(VoiceFailureReason.MISSING_PERMISSION)
        } catch (error: TimeoutException) {
            LOG.warn("Timed out waiting for the diagnostic voice connection")
            primaryFailure = error
            outcome = VoicePlaybackResult.Failed(VoiceFailureReason.CONNECT_TIMEOUT)
        } catch (error: VoiceConnectionException) {
            LOG.warn("The diagnostic voice connection was lost")
            primaryFailure = error
            outcome = VoicePlaybackResult.Failed(VoiceFailureReason.CONNECTION_LOST)
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            primaryFailure = error
            outcome = VoicePlaybackResult.Failed(VoiceFailureReason.INTERNAL_ERROR)
        } catch (error: Exception) {
            LOG.warn("The diagnostic voice playback failed ({})", error.javaClass.simpleName)
            primaryFailure = error
            outcome = VoicePlaybackResult.Failed(VoiceFailureReason.INTERNAL_ERROR)
        } finally {
            val cleanupOutcome = cleanup(
                audioManager = audioManager,
                guild = guild,
                waiter = waiter,
                activeSendHandler = sendHandler,
                previousConnectTimeout = previousConnectTimeout,
                previousAutoReconnect = previousAutoReconnect,
                previousListener = previousListener,
                previousSendingHandler = previousSendingHandler,
            )
            outcome = reconcileOutcome(
                outcome = outcome,
                primaryFailure = primaryFailure,
                cleanupOutcome = cleanupOutcome,
                terminalReason = waiter.terminalMessage(),
            )
        }

        return outcome
    }

    private suspend fun framesOutcome(
        signal: PlaybackSignal.Frames,
        sendHandler: JdaAudioSendHandler,
        waiter: VoiceConnectionWaiter,
        source: AudioFrameSource,
    ): VoicePlaybackResult {
        if (!signal.completed) {
            // Prefer a real connection loss over a concurrent tone timeout.
            waiter.terminalMessage()?.let { throw VoiceConnectionException(it) }
            return if (sendHandler.failed) {
                VoicePlaybackResult.Failed(VoiceFailureReason.INTERNAL_ERROR)
            } else {
                VoicePlaybackResult.Failed(VoiceFailureReason.TONE_TIMEOUT)
            }
        }

        // Both race arms can be ready at once; prefer an already recorded loss before flushing.
        waiter.checkTerminal()
        delay(flushMillis)
        waiter.checkTerminal()
        return if (sendHandler.framesProvided == source.expectedFrameCount) {
            VoicePlaybackResult.Completed(
                framesProvided = sendHandler.framesProvided,
                expectedFrames = source.expectedFrameCount,
            )
        } else {
            VoicePlaybackResult.Failed(VoiceFailureReason.INCOMPLETE_PLAYBACK)
        }
    }

    /**
     * Races frame completion against the waiter's terminal failure signal.
     *
     * The "frames" arm completes when the expected frames have been handed to JDA's send loop
     * or the source ended; it is a hand-off signal, not network delivery.
     *
     * A connection loss therefore ends playback promptly and maps to
     * [VoiceFailureReason.CONNECTION_LOST] instead of waiting for the tone timeout. The losing
     * wait is cancelled; both waits are coroutine-cancellable.
     */
    private suspend fun awaitPlaybackSignal(
        sendHandler: JdaAudioSendHandler,
        waiter: VoiceConnectionWaiter,
        timeoutMillis: Long,
    ): PlaybackSignal = coroutineScope {
        val frames = async { PlaybackSignal.Frames(sendHandler.awaitCompletionCancellable(timeoutMillis)) }
        val terminal = async { PlaybackSignal.Terminal(waiter.awaitTerminalFailure()) }

        try {
            select<PlaybackSignal> {
                frames.onAwait { it }
                terminal.onAwait { it }
            }
        } finally {
            frames.cancel()
            terminal.cancel()
        }
    }

    /**
     * Attempts to restore the audio manager and classifies every cleanup failure.
     *
     * Runs under [NonCancellable] so coroutine cancellation cannot skip the cleanup attempt.
     * Each step is best-effort: a step that fails is recorded instead of aborting the rest, so
     * the remaining restores are still attempted.
     * The active send handler is closed before it is detached from the [AudioManager], so it
     * cannot hand a prefetched frame to JDA after the session starts closing. The handler does
     * not own the [AudioFrameSource], so closing it never closes the caller's source.
     * Failures are split into disconnect failures (the voice connection was not confirmed
     * closed) and other cleanup failures, so the caller can keep
     * [VoiceFailureReason.DISCONNECT_UNCONFIRMED] only for a real unconfirmed disconnect.
     * [CleanupOutcome.failure] combines every failure with suppressed exceptions so none is
     * lost, and [CleanupOutcome.fatalFailure] keeps an `Error` or cancellation visible for rethrow.
     */
    private suspend fun cleanup(
        audioManager: AudioManager,
        guild: Guild,
        waiter: VoiceConnectionWaiter,
        activeSendHandler: JdaAudioSendHandler?,
        previousConnectTimeout: Long,
        previousAutoReconnect: Boolean,
        previousListener: ConnectionListener?,
        previousSendingHandler: AudioSendHandler?,
    ): CleanupOutcome = withContext(NonCancellable) {
        val disconnectFailures = mutableListOf<Throwable>()
        val otherFailures = mutableListOf<Throwable>()

        fun record(failures: MutableList<Throwable>, message: String, error: Throwable) {
            failures += error
            LOG.warn("{} ({})", message, error.javaClass.simpleName)
        }

        try {
            // Invalidate the handler first: a frame prefetched for the send loop is dropped and
            // the handler can no longer provide audio, even if JDA still holds a reference.
            activeSendHandler?.close()
        } catch (error: Throwable) {
            record(otherFailures, "Could not stop the voice send handler", error)
        }

        try {
            audioManager.setSendingHandler(null)
        } catch (error: Throwable) {
            record(otherFailures, "Could not clear the voice send handler", error)
        }

        try {
            val status = audioManager.connectionStatus
            if (audioManager.isConnected || status != ConnectionStatus.NOT_CONNECTED) {
                val disconnectRequest = waiter.expectDisconnect()
                audioManager.closeAudioConnection()
                if (!disconnectRequest.awaitCancellable(disconnectTimeoutMillis)) {
                    record(
                        disconnectFailures,
                        "The requested voice disconnect was not confirmed",
                        VoiceConnectionException("The requested voice disconnect was not confirmed."),
                    )
                }
            } else if (waiter.hasPendingConnectionRequest()) {
                waiter.expectDisconnect()
                audioManager.closeAudioConnection()
                if (!awaitSelfVoiceStateCleared(guild)) {
                    record(
                        disconnectFailures,
                        "The pending voice connection cancellation was not confirmed",
                        VoiceConnectionException(
                            "The pending voice connection cancellation was not confirmed.",
                        ),
                    )
                }
            }
        } catch (error: Throwable) {
            record(disconnectFailures, "Could not close the diagnostic voice connection", error)
        } finally {
            try {
                audioManager.setConnectionListener(previousListener)
            } catch (error: Throwable) {
                record(otherFailures, "Could not restore the voice connection listener", error)
            }
            try {
                audioManager.setAutoReconnect(previousAutoReconnect)
            } catch (error: Throwable) {
                record(otherFailures, "Could not restore auto-reconnect", error)
            }
            try {
                audioManager.setConnectTimeout(previousConnectTimeout)
            } catch (error: Throwable) {
                record(otherFailures, "Could not restore the voice connect timeout", error)
            }
            try {
                audioManager.setSendingHandler(previousSendingHandler)
            } catch (error: Throwable) {
                record(otherFailures, "Could not restore the voice send handler", error)
            }
        }

        CleanupOutcome(
            disconnectFailures = disconnectFailures,
            otherFailures = otherFailures,
        )
    }

    /**
     * Preserves the primary outcome and never reports a clean success when cleanup failed.
     *
     * A fatal cleanup failure (`Error` or [CancellationException]) is never converted into an
     * ordinary result: it is rethrown, with the primary playback/connect failure attached, unless
     * the primary is itself fatal and already propagating, in which case the cleanup failures are
     * attached to it and it keeps precedence.
     *
     * When playback completed but cleanup failed normally, the result is
     * [VoiceFailureReason.DISCONNECT_UNCONFIRMED] if the voice connection itself was not
     * confirmed closed, and [VoiceFailureReason.CLEANUP_FAILED] when the disconnect was
     * confirmed but another cleanup step failed. Otherwise the primary failure reason is kept
     * and the cleanup failure is attached as a suppressed exception to the primary throwable
     * (or to a synthetic anchor) before it is logged.
     */
    private fun reconcileOutcome(
        outcome: VoicePlaybackResult,
        primaryFailure: Throwable?,
        cleanupOutcome: CleanupOutcome,
        terminalReason: String?,
    ): VoicePlaybackResult {
        // A terminal status observed after the last explicit check still means the connection
        // was lost; a completed tone must not be reported as a clean success.
        val reconciled = if (outcome is VoicePlaybackResult.Completed && terminalReason != null) {
            LOG.warn("The diagnostic voice connection was lost before cleanup finished")
            VoicePlaybackResult.Failed(VoiceFailureReason.CONNECTION_LOST)
        } else {
            outcome
        }

        val cleanupFatal = cleanupOutcome.fatalFailure()
        if (cleanupFatal != null) {
            preserveFatalCleanupFailure(primaryFailure, cleanupOutcome, cleanupFatal)
        }

        val cleanupFailure = cleanupOutcome.failure() ?: return reconciled
        val anchor = primaryFailure
            ?: VoiceConnectionException("Voice playback ended with $reconciled")
        if (anchor !== cleanupFailure) {
            anchor.addSuppressed(cleanupFailure)
        }
        LOG.error("Voice cleanup did not finish cleanly; primary outcome was {}", reconciled, anchor)

        return when {
            reconciled !is VoicePlaybackResult.Completed -> reconciled
            cleanupOutcome.disconnectConfirmed -> VoicePlaybackResult.Failed(VoiceFailureReason.CLEANUP_FAILED)
            else -> VoicePlaybackResult.Failed(VoiceFailureReason.DISCONNECT_UNCONFIRMED)
        }
    }

    /**
     * Rethrows a fatal cleanup failure without turning it into an ordinary result.
     *
     * When the primary playback/connect failure is itself fatal and already propagating (a
     * cancellation or `Error` rethrown from the playback block), it keeps precedence: the cleanup
     * failures are attached to it and it is rethrown again, so it is not replaced by a cleanup
     * failure. Otherwise the fatal cleanup failure wins and the primary playback/connect failure is
     * attached as suppressed before the fatal cleanup failure is thrown.
     *
     * This method always throws, so no `DISCONNECT_UNCONFIRMED` or `CLEANUP_FAILED` result can
     * represent a fatal cleanup failure.
     */
    private fun preserveFatalCleanupFailure(
        primaryFailure: Throwable?,
        cleanupOutcome: CleanupOutcome,
        cleanupFatal: Throwable,
    ): Nothing {
        if (primaryFailure != null && primaryFailure.isFatal()) {
            cleanupOutcome.attachTo(primaryFailure)
            LOG.error(
                "Voice cleanup failed with a fatal error while the playback failure propagates",
                cleanupFatal,
            )
            throw primaryFailure
        }

        if (primaryFailure != null && primaryFailure !== cleanupFatal) {
            cleanupFatal.addSuppressed(primaryFailure)
        }
        cleanupOutcome.attachTo(cleanupFatal)
        LOG.error("Voice cleanup failed with a fatal error", cleanupFatal)
        throw cleanupFatal
    }

    /**
     * True when this [AudioManager] already owns an audio connection, either connected or still
     * connecting, so no new session may be opened on it.
     *
     * JDA 6.7.0 creates its internal `AudioConnection` as soon as the voice-server update
     * arrives, so [AudioManager.isConnected] is already true while the connection walks through
     * the `CONNECTING_*` statuses, and it stays true through `CONNECTED`, `AUDIO_REGION_CHANGE`
     * and the reconnecting `ERROR_*` statuses until the manager closes the connection.
     * [AudioManager.getConnectionStatus] can only report [ConnectionStatus.NOT_CONNECTED] while
     * no such connection exists, so any other status belongs to a session this port must not
     * adopt. A voice-state request that has not yet produced an `AudioConnection` has no public
     * "attempting" flag in JDA 6.7.0; the port's own single-session guard covers its pending
     * request.
     */
    private fun AudioManager.hasActiveSession(): Boolean =
        isConnected || connectionStatus != ConnectionStatus.NOT_CONNECTED

    /**
     * Confirms that a queued connection request was drained by observing the bot's own voice
     * state appear and then clear. JDA 6.7.0 exposes no public "attempting" flag, so a request
     * that never became visible cannot be confirmed as cancelled.
     */
    private suspend fun awaitSelfVoiceStateCleared(guild: Guild): Boolean {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(disconnectTimeoutMillis)
        var observedVoiceState = guild.selfMember.voiceState?.channel != null

        while (System.nanoTime() < deadline) {
            val inVoiceChannel = guild.selfMember.voiceState?.channel != null
            if (inVoiceChannel) {
                observedVoiceState = true
            } else if (observedVoiceState) {
                return true
            }

            delay(selfVoiceStatePollMillis)
        }

        return observedVoiceState && guild.selfMember.voiceState?.channel == null
    }

    private sealed interface PlaybackSignal {
        data class Frames(val completed: Boolean) : PlaybackSignal
        data class Terminal(val reason: String) : PlaybackSignal
    }

    /**
     * Collected cleanup failures, split by whether they concern the voice disconnect itself.
     * Multiple failures are combined into one throwable with the rest attached as suppressed,
     * so the caller can preserve the primary playback failure without losing the lifecycle
     * error. A fatal failure (`Error` or [CancellationException]) is tracked separately so the
     * caller can rethrow it instead of reporting an ordinary result.
     */
    private class CleanupOutcome(
        private val disconnectFailures: List<Throwable>,
        private val otherFailures: List<Throwable>,
    ) {

        private val failures: List<Throwable> get() = disconnectFailures + otherFailures

        /** True when every disconnect attempt was confirmed; false when the state is unconfirmed. */
        val disconnectConfirmed: Boolean get() = disconnectFailures.isEmpty()

        /** First `Error` or [CancellationException] from any step, or null when none is fatal. */
        fun fatalFailure(): Throwable? = failures.firstOrNull { it.isFatal() }

        /** Adds every collected failure except [target] as a suppressed exception of [target]. */
        fun attachTo(target: Throwable) {
            failures.forEach { failure ->
                if (failure !== target && !target.suppressed.contains(failure)) {
                    target.addSuppressed(failure)
                }
            }
        }

        fun failure(): Throwable? {
            val collected = failures
            return when (collected.size) {
                0 -> null

                1 -> collected.single()

                else -> collected.first().also { primary ->
                    collected.drop(1).forEach(primary::addSuppressed)
                }
            }
        }
    }

    private sealed interface Target {
        data class Ready(val guild: Guild, val channel: VoiceChannel) : Target
        data class Rejected(val reason: VoiceRequestRejection) : Target
    }

    private companion object {
        private val LOG = LoggerFactory.getLogger(JdaVoiceSessionPort::class.java)

        private const val DEFAULT_CONNECT_TIMEOUT_MILLIS = 20_000L
        private const val DEFAULT_DAVE_SETTLE_MILLIS = 1_500L
        private const val DEFAULT_TONE_TIMEOUT_MILLIS = 15_000L
        private const val DEFAULT_FLUSH_MILLIS = 300L
        private const val DEFAULT_DISCONNECT_TIMEOUT_MILLIS = 5_000L
        private const val DEFAULT_SELF_VOICE_STATE_POLL_MILLIS = 100L
    }
}

private fun GuildId.toJdaId(): Long = value.toJdaLong("Guild id")

private fun UserId.toJdaId(): Long = value.toJdaLong("User id")

/**
 * True for cleanup failures that must never be converted into an ordinary voice result.
 *
 * `Error`s and [CancellationException]s are control flow, not best-effort cleanup noise: the
 * playback path rethrows them with the primary playback/connect failure preserved.
 */
private fun Throwable.isFatal(): Boolean = this is Error || this is CancellationException

private fun ULong.toJdaLong(label: String): Long {
    require(this <= Long.MAX_VALUE.toULong()) {
        "$label $this does not fit in JDA's signed 64-bit id range"
    }
    return toLong()
}
