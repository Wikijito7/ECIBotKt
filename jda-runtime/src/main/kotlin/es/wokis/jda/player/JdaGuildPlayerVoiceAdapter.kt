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
 *
 * **Event ownership.** This class is the single subscriber of [PlayerEngine.events]; it derives the
 * [presentation] state from those events. Nothing else may collect the stream.
 *
 * **Stream lifecycle hazards this class handles:**
 *
 * - A handler/stream that observed [es.wokis.player.engine.audio.PlayerAudioState.Ended] stays
 *   exhausted forever; [JdaAudioSendHandler.canProvide] then always returns `false`. Connecting or
 *   reconnecting therefore always installs a **fresh** stream and handler, instead of reusing the
 *   one left behind by a drained queue or a stopped engine, which would otherwise leave a later
 *   play silently mute.
 * - [connect] is only called after a load produced an active track (see the command controller), so
 *   a voice connection is not opened against an engine whose playback already terminated. Open a
 *   connection too early and the initial `Ended` stream would be exhausted before the first frame.
 * - An [net.dv8tion.jda.api.managers.AudioManager] may already own a session started by another
 *   owner, for example the Phase 2C diagnostic walkthrough. Taking it over would move a session this
 *   adapter does not own, so [connect] refuses (`false`) and logs instead of racing it. The
 *   diagnostic port applies the reverse guard by rejecting a request when any session is active, so
 *   the two voice paths stay mutually exclusive: run the walkthrough and the real player one at a
 *   time in a guild.
 *
 * [serialDispatcher] is that single-coroutine dispatcher and is owned by this adapter: the default
 * expression creates a **fresh** `Dispatchers.Default.limitedParallelism(1)` view for every adapter
 * instance, so unrelated guilds never share a serialization queue. Never pass one process-wide
 * single-thread dispatcher from a multi-guild composition root — do that and every guild's
 * connect/disconnect queues behind the slowest one. Inject a dispatcher to observe or control
 * serialization without changing connect/disconnect/close semantics.
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

    /** Event-derived presentation state; the adapter remains the only engine-event subscriber. */
    val presentation: StateFlow<JdaPlayerPresentation> = presentationState.asStateFlow()

    private var stream: ContinuousPlayerAudioStream? = null
    private var handler: JdaAudioSendHandler? = null
    private var eventJob: Job? = null
    private var channelId: Long? = null

    /**
     * Last channel this adapter asked JDA to join.
     *
     * Kept after a disconnect because JDA closes the connection asynchronously: an immediate
     * reconnect to the same channel must not be mistaken for a session owned by somebody else.
     */
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

    /**
     * Opens or refreshes the voice connection to [channel] and feeds it the player stream.
     *
     * The audio pipeline is recreated on every call; see the class documentation for why an
     * exhausted handler must never be reused. The voice connection itself is only reopened when the
     * target channel differs from the one currently connected.
     *
     * @return `true` when this adapter owns the connection after the call, `false` when it refused
     * to take over a session owned by another voice path, or when the adapter is closing.
     */
    suspend fun connect(channel: VoiceChannel): Boolean {
        if (closeRequested.get()) return false
        return withContext(serial) { connectLocked(channel) }
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
            // Keep the JDA connection, replace only the exhausted/terminated audio pipeline.
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

            // A fast close() may have won while this connect was queued; never leave the stale
            // session installed.
            if (closeRequested.get()) {
                disconnectLocked()
            }
            true
        } catch (error: Throwable) {
            disconnectLocked()
            throw error
        }
    }

    /**
     * True when the guild's audio manager holds a session this adapter did not open.
     *
     * JDA closing a connection is asynchronous, so the last channel this adapter joined is always
     * accepted; only a session on a channel this adapter never requested is treated as foreign.
     */
    private fun isForeignSession(channel: VoiceChannel): Boolean =
        channelId == null && requestedChannelId != channel.idLong && isConnectedOrConnecting()

    /** Drops the audio pipeline without touching the JDA connection (used before a re-install). */
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

    /**
     * Folds one engine event into [presentation].
     *
     * The state is derived from the events themselves (`QueueChanged` carries the authoritative
     * snapshot) instead of polling the engine, so the adapter does not read engine state from a
     * different thread than the engine actor.
     */
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

    /** Fast, non-blocking signal. Use [shutdown] to await JDA cleanup. */
    override fun close() {
        closeRequested.set(true)
        eventJob?.cancel()
        eventJob = null
    }

    private companion object {
        private val LOG = LoggerFactory.getLogger(JdaGuildPlayerVoiceAdapter::class.java)
    }
}
