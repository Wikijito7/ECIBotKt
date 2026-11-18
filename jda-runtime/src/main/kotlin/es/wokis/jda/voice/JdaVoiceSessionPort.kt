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
 * Resolves the invoking user's cached voice state, connects the bot to their regular
 * [VoiceChannel] and sends pre-encoded Opus frames through a [JdaAudioSendHandler]. At most one
 * playback runs at a time, and the connect/playback waits are coroutine-cancellable, so
 * cancellation starts cleanup immediately instead of waiting for a timeout.
 *
 * Cleanup is attempted under `NonCancellable` with bounded waits, so cancellation cannot skip it;
 * each step is best-effort and its failure is surfaced rather than reported as a clean success. It
 * closes the active handler (dropping any prefetched frame) before detaching it from the
 * [AudioManager]; the handler never closes the caller-owned source.
 *
 * A session already connected or connecting in the target guild is never moved, replaced or
 * closed: the request is rejected with [VoiceRequestRejection.VOICE_SESSION_ALREADY_ACTIVE]
 * before any manager state is mutated. No gateway identifier is logged or returned to the user.
 *
 * Failure precedence: cleanup never downgrades a fatal primary failure (`Error` or cancellation) —
 * it keeps precedence and cleanup failures are attached as suppressed. When playback completed but
 * cleanup failed, the result is [VoiceFailureReason.DISCONNECT_UNCONFIRMED] if the connection was
 * not confirmed closed, otherwise [VoiceFailureReason.CLEANUP_FAILED]; a fatal cleanup failure is
 * rethrown.
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

    private fun throwSessionLost(reason: String): Nothing = throw VoiceConnectionException(reason)

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
                is PlaybackSignal.Terminal -> throwSessionLost(signal.reason)
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

        // Both race arms can be ready at once; prefer a recorded loss before flushing.
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
     * Races frame completion against the waiter's terminal signal, so a connection loss ends
     * playback promptly instead of waiting for the tone timeout. The frames arm is a hand-off
     * signal, not network delivery; the losing wait is cancelled.
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
     * Restores the audio manager under [NonCancellable], recording each failure individually so the
     * remaining steps still run. Disconnect failures stay separate from other cleanup failures.
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
            // Invalidate the handler first so it cannot provide a prefetched frame while closing.
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
     * Preserves the primary outcome and never reports a clean success when cleanup failed; see the
     * class contract for the failure mapping. A terminal status observed after the last check still
     * means the connection was lost.
     */
    private fun reconcileOutcome(
        outcome: VoicePlaybackResult,
        primaryFailure: Throwable?,
        cleanupOutcome: CleanupOutcome,
        terminalReason: String?,
    ): VoicePlaybackResult {
        // A terminal status after the last check still means the connection was lost.
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
     * Rethrows a fatal cleanup failure, keeping a fatal primary failure's precedence and attaching
     * the other failures as suppressed. Always throws, so no ordinary result represents it.
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
     * True when this [AudioManager] already owns a connection, connected or still connecting.
     *
     * JDA 6.7.0 creates its `AudioConnection` as soon as the voice-server update arrives, so
     * [AudioManager.isConnected] already covers the `CONNECTING_*` and reconnecting `ERROR_*`
     * statuses; [ConnectionStatus.NOT_CONNECTED] only appears when no connection exists. A pending
     * voice-state request has no public "attempting" flag, so the port's own guard covers it.
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
     * Cleanup failures split by whether they concern the disconnect itself. Fatal failures are
     * tracked separately so the caller can rethrow them instead of reporting an ordinary result.
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

/** True for failures that must never become an ordinary voice result (`Error` or cancellation). */
private fun Throwable.isFatal(): Boolean = this is Error || this is CancellationException

private fun ULong.toJdaLong(label: String): Long {
    require(this <= Long.MAX_VALUE.toULong()) {
        "$label does not fit in JDA's signed 64-bit id range"
    }
    return toLong()
}
