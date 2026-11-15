package es.wokis.jda.player

import es.wokis.jda.voice.JdaAudioSendHandler
import es.wokis.player.engine.PlayerEngine
import es.wokis.player.engine.audio.ContinuousPlayerAudioStream
import es.wokis.player.engine.event.PlayerEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import net.dv8tion.jda.api.audio.hooks.ConnectionStatus
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel
import java.util.concurrent.atomic.AtomicBoolean

private const val CLOSE_TIMEOUT_MILLIS = 5_000L

/**
 * Long-lived JDA voice adapter for one guild's shared [PlayerEngine].
 *
 * The adapter owns the voice connection and the continuous audio stream, and reuses the existing
 * [JdaAudioSendHandler] for the send path. It does not create a second `AudioSendHandler` and does
 * not own the engine's queue; it only maps the neutral continuous stream (frame / paused silence /
 * ended) to JDA's 20 ms send ticks.
 *
 * [connect], [disconnect] and [shutdown] are suspend and run on a single-coroutine dispatcher, so
 * no JDA event/coroutine caller blocks a thread and connect/disconnect/close cannot interleave.
 * [close] is only a fast idempotent signal: it prevents new operations and cancels the event
 * collector without touching JDA. Production shutdown awaits [shutdown].
 */
internal class JdaGuildPlayerVoiceAdapter(
    private val guild: Guild,
    private val engine: PlayerEngine,
    private val scope: CoroutineScope,
) : AutoCloseable {

    private val serial = Dispatchers.Default.limitedParallelism(1)
    private val closeRequested = AtomicBoolean(false)
    private val shutdownComplete = AtomicBoolean(false)

    private var stream: ContinuousPlayerAudioStream? = null
    private var handler: JdaAudioSendHandler? = null
    private var eventJob: Job? = null
    private var channelId: Long? = null

    init {
        eventJob = scope.launch {
            engine.events.collect { event ->
                if (event is PlayerEvent.Stopped) {
                    disconnect()
                }
            }
        }
    }

    /** Opens or keeps a voice connection to [channel] and starts feeding the player stream. */
    suspend fun connect(channel: VoiceChannel) {
        if (closeRequested.get()) return
        withContext(serial) {
            connectLocked(channel)
        }
    }

    /** Closes the handler/stream and requests the voice connection to close. */
    suspend fun disconnect() {
        if (closeRequested.get()) return
        withContext(serial) {
            if (!closeRequested.get()) {
                disconnectLocked()
            }
        }
    }

    /**
     * Full adapter teardown: serialized disconnect and stream/handler close.
     *
     * Idempotent; callers that need the JDA session closed must use this method, not [close].
     */
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

    private fun connectLocked(channel: VoiceChannel) {
        if (closeRequested.get()) return
        if (channelId == channel.idLong && isConnectedOrConnecting()) return

        // Only a session this adapter owns is closed; an unrelated JDA session is never moved.
        disconnectLocked()

        val audioStream = engine.audioStream()
        val sendHandler = JdaAudioSendHandler.continuous(audioStream)
        stream = audioStream
        handler = sendHandler
        channelId = channel.idLong

        try {
            guild.audioManager.setSendingHandler(sendHandler)
            guild.audioManager.openAudioConnection(channel)
        } catch (error: Throwable) {
            disconnectLocked()
            throw error
        }

        // A fast close() may have won while this connect was queued; never leave the stale
        // session installed.
        if (closeRequested.get()) {
            disconnectLocked()
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

    private fun isConnectedOrConnecting(): Boolean {
        val audioManager = guild.audioManager
        return audioManager.isConnected || audioManager.connectionStatus != ConnectionStatus.NOT_CONNECTED
    }

    /** Fast, non-blocking signal. Use [shutdown] to await JDA cleanup. */
    override fun close() {
        closeRequested.set(true)
        eventJob?.cancel()
        eventJob = null
    }
}
