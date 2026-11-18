package es.wokis.jda.player

import es.wokis.jda.voice.JdaAudioSendHandler
import es.wokis.player.engine.PlayerEngine
import es.wokis.player.engine.audio.ContinuousPlayerAudioStream
import es.wokis.player.engine.event.PlayerEvent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import net.dv8tion.jda.api.audio.hooks.ConnectionStatus
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel
import net.dv8tion.jda.api.managers.AudioManager
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicBoolean

private const val CLOSE_TIMEOUT_MILLIS = 5_000L
private const val DISCONNECT_POLL_MILLIS = 50L

private fun AudioManager.isConnectedOrConnecting(): Boolean =
    isConnected || connectionStatus != ConnectionStatus.NOT_CONNECTED

/**
 * JDA's `closeAudioConnection()` is queued on its audio pool, so a fixed delay may reopen while the
 * old `AudioConnection` still exists and `openAudioConnection()` silently no-ops. Poll until JDA
 * reports no connection at all, matching the predicate every adapter guard uses; the caller bounds
 * this wait with a timeout.
 */
private suspend fun awaitAudioDisconnect(guild: Guild) {
    while (guild.audioManager.isConnectedOrConnecting()) {
        delay(DISCONNECT_POLL_MILLIS)
    }
}

/** Identity token for one reconnect attempt; a new attempt, close or shutdown invalidates it. */
private class ReconnectAttempt

/** Outcome of the serialized reconnect begin phase. */
private sealed interface ReconnectBegin {
    class Started(val attempt: ReconnectAttempt) : ReconnectBegin

    data class Refused(val result: JdaPlayerReconnectResult) : ReconnectBegin
}

/**
 * Long-lived voice adapter for one guild's [PlayerEngine]; the sole collector of
 * [PlayerEngine.events] and sole owner of the voice connection and audio pipeline.
 *
 * A stream/handler that observed `Ended` stays exhausted, so every connect installs a fresh
 * pipeline after a load produced an active track. [connect] refuses a session opened by another
 * voice path instead of racing it. Suspend operations run on a per-adapter serial dispatcher, so
 * guilds never share a serialization queue.
 *
 * Reconnect is split into a serialized begin (detach and close the owned session) and finish
 * (reopen once JDA confirms the close), so the serial dispatcher is never held while JDA completes
 * its asynchronous disconnect and [shutdown] can still make progress.
 */
internal class JdaGuildPlayerVoiceAdapter(
    private val guild: Guild,
    private val engine: PlayerEngine,
    private val scope: CoroutineScope,
    serialDispatcher: CoroutineDispatcher = Dispatchers.Default.limitedParallelism(1),
    private val disconnectConfirmation: suspend () -> Unit = { awaitAudioDisconnect(guild) },
) : AutoCloseable {

    private val serial = serialDispatcher
    private val closeRequested = AtomicBoolean(false)
    private val shutdownComplete = AtomicBoolean(false)
    private val presentationState = MutableStateFlow(JdaPlayerPresentation())

    val presentation: StateFlow<JdaPlayerPresentation> = presentationState.asStateFlow()

    private var eventJob: Job? = null
    private var stream: ContinuousPlayerAudioStream? = null
    private var handler: JdaAudioSendHandler? = null
    private var channelId: Long? = null

    /**
     * The adapter opened the current voice session and has not confirmed it closed yet. Survives a
     * timed-out close so a retry is not mistaken for a foreign session, and is dropped on an
     * explicit disconnect, shutdown, or when JDA reports the session gone.
     */
    private var ownsSession = false

    /** Non-null while a reconnect is between its begin and finish phases. */
    private var pendingReconnect: ReconnectAttempt? = null

    init {
        eventJob = scope.launch {
            engine.events.collect { event ->
                record(event)
                if (event is PlayerEvent.Stopped) {
                    disconnect()
                }
            }
        }
    }

    /** @return `false` when another voice path owns the session or the adapter is closing. */
    suspend fun connect(channel: VoiceChannel): Boolean {
        if (closeRequested.get()) return false
        return withContext(serial) { connectLocked(channel) }
    }

    /**
     * Kord parity: leave the owned session, wait for JDA to confirm the disconnect, then rejoin and
     * reinstall audio while the engine keeps its track, queue and paused position untouched.
     */
    suspend fun reconnect(channel: VoiceChannel): JdaPlayerReconnectResult {
        if (closeRequested.get()) return JdaPlayerReconnectResult.NotConnected
        return when (val begin = withContext(serial) { beginReconnect() }) {
            is ReconnectBegin.Refused -> begin.result
            is ReconnectBegin.Started -> finishReconnect(begin.attempt, channel)
        }
    }

    suspend fun disconnect() {
        if (closeRequested.get()) return
        withContext(serial) {
            if (!closeRequested.get()) {
                pendingReconnect = null
                disconnectLocked()
            }
        }
    }

    /** Full teardown; use this instead of [close] when the JDA session must close. */
    suspend fun shutdown() {
        if (!shutdownComplete.compareAndSet(false, true)) return
        closeRequested.set(true)
        eventJob?.cancel()
        eventJob = null
        withTimeoutOrNull(CLOSE_TIMEOUT_MILLIS) {
            withContext(serial) {
                pendingReconnect = null
                disconnectLocked()
            }
        }
    }

    private fun connectLocked(channel: VoiceChannel): Boolean {
        if (closeRequested.get()) return false
        val refusal = when {
            pendingReconnect != null ->
                "Refusing to connect while a voice reconnect is still closing the old session"

            isForeignSession() ->
                "Refusing to take over a voice session owned by another voice path; " +
                    "disconnect it before using the real player"

            else -> null
        }
        if (refusal != null) {
            LOG.warn(refusal)
            return false
        }

        val sameChannelSession =
            channelId == channel.idLong && ownsSession && guild.audioManager.isConnectedOrConnecting()
        if (sameChannelSession) {
            // Keep the connection; only the exhausted pipeline is replaced.
            releasePipeline()
        } else {
            disconnectLocked()
        }

        installPipeline(channel.idLong)
        return try {
            guild.audioManager.setSendingHandler(requireNotNull(handler))
            if (!sameChannelSession) {
                guild.audioManager.openAudioConnection(channel)
            }

            // A queued close() may have won; never leave the stale session installed.
            if (closeRequested.get()) {
                disconnectLocked()
            }
            true
        } catch (error: Throwable) {
            disconnectLocked()
            throw error
        }
    }

    private fun beginReconnect(): ReconnectBegin {
        val refusal = reconnectRefusal()
        if (refusal != null) return ReconnectBegin.Refused(refusal)
        val attempt = ReconnectAttempt()
        pendingReconnect = attempt
        return try {
            closeVoiceSession()
            ReconnectBegin.Started(attempt)
        } catch (error: Throwable) {
            pendingReconnect = null
            throw error
        }
    }

    /** @return the refusal to report, or `null` when the owned session can be closed and reopened. */
    private fun reconnectRefusal(): JdaPlayerReconnectResult? {
        val refusal = when {
            closeRequested.get() -> JdaPlayerReconnectResult.NotConnected
            pendingReconnect != null -> JdaPlayerReconnectResult.InProgress
            !ownsSession -> if (guild.audioManager.isConnectedOrConnecting()) {
                JdaPlayerReconnectResult.ForeignSession
            } else {
                JdaPlayerReconnectResult.NotConnected
            }

            // Kord parity: reconnect never falls back to connect when JDA has no live session.
            !guild.audioManager.isConnectedOrConnecting() -> JdaPlayerReconnectResult.NotConnected
            else -> null
        }
        if (refusal == JdaPlayerReconnectResult.InProgress) {
            LOG.warn("Refusing to reconnect while another reconnect is still closing the old session")
        }
        if (refusal == JdaPlayerReconnectResult.ForeignSession) {
            LOG.warn("Refusing to reconnect a voice session owned by another voice path")
        }
        if (refusal == JdaPlayerReconnectResult.NotConnected && ownsSession &&
            !guild.audioManager.isConnectedOrConnecting()
        ) {
            // JDA no longer reports our session, so the ownership is stale and must not mask a
            // later foreign session.
            ownsSession = false
        }
        return refusal
    }

    private suspend fun finishReconnect(
        attempt: ReconnectAttempt,
        channel: VoiceChannel,
    ): JdaPlayerReconnectResult = try {
        val confirmed = withTimeoutOrNull(CLOSE_TIMEOUT_MILLIS) { disconnectConfirmation() } != null
        withContext(serial) { completeReconnect(attempt, channel, confirmed) }
    } catch (error: Throwable) {
        // Any failure must release this attempt's token, or later reconnects stay blocked as InProgress.
        withContext(NonCancellable + serial) {
            if (pendingReconnect === attempt) pendingReconnect = null
        }
        throw error
    }

    private fun completeReconnect(
        attempt: ReconnectAttempt,
        channel: VoiceChannel,
        confirmed: Boolean,
    ): JdaPlayerReconnectResult {
        // close() only sets the fast abort flag; disconnect() and shutdown() clear the token, which
        // marks this attempt aborted. Otherwise, even with closeRequested set, completion clears it.
        val aborted = pendingReconnect !== attempt
        if (!aborted) pendingReconnect = null
        return when {
            aborted || closeRequested.get() -> JdaPlayerReconnectResult.NotConnected
            !confirmed -> {
                LOG.warn("Timed out waiting for the old JDA voice connection to close")
                JdaPlayerReconnectResult.TimedOut
            }

            else -> {
                // Drop ownership before opening: pipeline construction can throw before
                // installPipeline reclaims it, so a failed reopen must not look owned.
                ownsSession = false
                openReconnected(channel)
            }
        }
    }

    private fun openReconnected(channel: VoiceChannel): JdaPlayerReconnectResult {
        installPipeline(channel.idLong)
        return try {
            guild.audioManager.setSendingHandler(requireNotNull(handler))
            guild.audioManager.openAudioConnection(channel)
            if (closeRequested.get()) {
                disconnectLocked()
                JdaPlayerReconnectResult.NotConnected
            } else {
                JdaPlayerReconnectResult.Reconnected
            }
        } catch (error: Throwable) {
            disconnectLocked()
            throw error
        }
    }

    /** Installs a fresh pipeline and claims the session before JDA opens it. */
    private fun installPipeline(targetChannelId: Long) {
        val audioStream = engine.audioStream()
        try {
            handler = JdaAudioSendHandler.continuous(audioStream)
        } catch (error: Throwable) {
            audioStream.close()
            throw error
        }
        stream = audioStream
        channelId = targetChannelId
        ownsSession = true
    }

    /** A session is foreign only when this adapter holds no ownership of the current connection. */
    private fun isForeignSession(): Boolean =
        !ownsSession && guild.audioManager.isConnectedOrConnecting()

    private fun releasePipeline() {
        val handlerToClose = handler
        val streamToClose = stream
        handler = null
        stream = null
        channelId = null
        try {
            handlerToClose?.close()
        } finally {
            streamToClose?.close()
        }
    }

    private fun disconnectLocked() {
        try {
            closeVoiceSession()
        } finally {
            ownsSession = false
        }
    }

    /** Closes the pipeline and requests JDA's close; the voice disconnect completes asynchronously. */
    private fun closeVoiceSession() {
        val handlerToClose = handler
        val streamToClose = stream
        handler = null
        stream = null
        channelId = null

        var failure: Throwable? = null
        try {
            handlerToClose?.close()
        } catch (error: Throwable) {
            failure = error
        }
        // Only the installed pipeline owns the sending-handler slot; a detached session has none.
        if (handlerToClose != null || streamToClose != null) {
            try {
                guild.audioManager.setSendingHandler(null)
            } catch (error: Throwable) {
                if (failure == null) failure = error else failure?.addSuppressed(error)
            }
        }
        if (ownsSession && guild.audioManager.isConnectedOrConnecting()) {
            try {
                guild.audioManager.closeAudioConnection()
            } catch (error: Throwable) {
                if (failure == null) failure = error else failure?.addSuppressed(error)
            }
        }
        try {
            streamToClose?.close()
        } catch (error: Throwable) {
            if (failure == null) failure = error else failure?.addSuppressed(error)
        }

        failure?.let { throw it }
    }

    /** Derives state from events (the queue snapshot is authoritative) instead of polling. */
    private fun record(event: PlayerEvent) {
        presentationState.update { current ->
            val updated = when (event) {
                is PlayerEvent.QueueChanged -> current.copy(
                    activeTrack = event.snapshot.hasActiveTrack,
                    paused = event.snapshot.paused,
                    queueSize = event.snapshot.queue.size,
                )

                is PlayerEvent.TrackStarted -> current.copy(activeTrack = true)

                is PlayerEvent.TrackEnded -> current.copy(
                    activeTrack = false,
                    queueSize = if (event.queueEmpty) 0 else current.queueSize,
                )

                PlayerEvent.Stopped -> current.copy(activeTrack = false, paused = false, queueSize = 0)

                else -> current
            }
            updated.copy(revision = current.revision + 1, lastEvent = event)
        }
    }

    /** Fast signal; use [shutdown] to await JDA cleanup. */
    override fun close() {
        closeRequested.set(true)
        eventJob?.cancel()
        eventJob = null
    }

    private companion object {
        private val LOG = LoggerFactory.getLogger(JdaGuildPlayerVoiceAdapter::class.java)
    }
}
