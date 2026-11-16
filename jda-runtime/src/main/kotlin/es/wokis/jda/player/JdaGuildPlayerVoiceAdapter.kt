package es.wokis.jda.player

import es.wokis.jda.voice.JdaAudioSendHandler
import es.wokis.player.engine.PlayerEngine
import es.wokis.player.engine.audio.ContinuousPlayerAudioStream
import es.wokis.player.engine.event.PlayerEvent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicBoolean

private const val CLOSE_TIMEOUT_MILLIS = 5_000L

/**
 * Long-lived voice adapter for one guild's [PlayerEngine]; the sole collector of
 * [PlayerEngine.events] and sole owner of the voice connection and audio pipeline.
 *
 * A stream/handler that observed `Ended` stays exhausted, so every connect installs a fresh
 * pipeline after a load produced an active track. [connect] refuses a session opened by another
 * voice path instead of racing it. Suspend operations run on a per-adapter serial dispatcher, so
 * guilds never share a serialization queue.
 */
internal class JdaGuildPlayerVoiceAdapter(
    private val guild: Guild,
    private val engine: PlayerEngine,
    private val scope: CoroutineScope,
    serialDispatcher: CoroutineDispatcher = Dispatchers.Default.limitedParallelism(1),
) : AutoCloseable {

    private val serial = serialDispatcher
    private val closeRequested = AtomicBoolean(false)
    private val shutdownComplete = AtomicBoolean(false)
    private val presentationState = MutableStateFlow(JdaPlayerPresentation())

    val presentation: StateFlow<JdaPlayerPresentation> = presentationState.asStateFlow()

    private var stream: ContinuousPlayerAudioStream? = null
    private var handler: JdaAudioSendHandler? = null
    private var eventJob: Job? = null
    private var channelId: Long? = null

    /** Kept after disconnect: async close means a same-channel reconnect must not look foreign. */
    private var requestedChannelId: Long? = null

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

    suspend fun disconnect() {
        if (closeRequested.get()) return
        withContext(serial) {
            if (!closeRequested.get()) {
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
                disconnectLocked()
            }
        }
    }

    private fun connectLocked(channel: VoiceChannel): Boolean {
        if (closeRequested.get()) return false
        if (isForeignSession(channel)) {
            LOG.warn(
                "Refusing to take over a voice session owned by another voice path; " +
                    "disconnect it before using the real player",
            )
            return false
        }

        val sameChannelSession = channelId == channel.idLong && isConnectedOrConnecting()
        if (sameChannelSession) {
            // Keep the connection; only the exhausted pipeline is replaced.
            releasePipeline()
        } else {
            disconnectLocked()
        }

        val audioStream = engine.audioStream()
        val sendHandler = JdaAudioSendHandler.continuous(audioStream)
        stream = audioStream
        handler = sendHandler
        channelId = channel.idLong
        requestedChannelId = channel.idLong

        return try {
            guild.audioManager.setSendingHandler(sendHandler)
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

    /** Only a session on a channel this adapter never requested is foreign. */
    private fun isForeignSession(channel: VoiceChannel): Boolean =
        channelId == null && requestedChannelId != channel.idLong && isConnectedOrConnecting()

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
        if (handler == null && stream == null && channelId == null) return

        val handlerToClose = handler
        val streamToClose = stream
        handler = null
        stream = null
        channelId = null

        try {
            handlerToClose?.close()
        } finally {
            try {
                guild.audioManager.setSendingHandler(null)
            } finally {
                try {
                    if (isConnectedOrConnecting()) {
                        guild.audioManager.closeAudioConnection()
                    }
                } finally {
                    streamToClose?.close()
                }
            }
        }
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

    private fun isConnectedOrConnecting(): Boolean {
        val audioManager = guild.audioManager
        return audioManager.isConnected || audioManager.connectionStatus != ConnectionStatus.NOT_CONNECTED
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
