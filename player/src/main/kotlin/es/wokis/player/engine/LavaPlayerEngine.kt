package es.wokis.player.engine

import com.github.topi314.lavasrc.ExtendedAudioPlaylist
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException
import com.sedmelluq.discord.lavaplayer.track.AudioItem
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason
import es.wokis.player.engine.audio.ContinuousPlayerAudioStream
import es.wokis.player.engine.event.PlayerEvent
import es.wokis.player.engine.model.PlayerSnapshot
import es.wokis.player.engine.model.PlayerTrack
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.min
import kotlin.time.Duration.Companion.milliseconds

private val LOG = LoggerFactory.getLogger("es.wokis.player.engine.LavaPlayerEngine")

private const val FIRST_BACKOFF_MILLIS = 250L
private const val MAX_BACKOFF_MILLIS = 2_000L
private const val MAX_RETRIES = 5

/**
 * Lavaplayer implementation of the shared per-guild player engine.
 *
 * Owns one [AudioPlayer] and one pending queue; a single-consumer mailbox serializes public
 * commands, and the [AudioEventAdapter] only enqueues into it, so one actor applies every
 * queue/current/pause transition in arrival order. Lavaplayer `AudioTrack` stays private.
 */
internal class LavaPlayerEngine internal constructor(
    private val manager: AudioPlayerManager,
    private val ownsManager: Boolean,
) : PlayerEngine {

    private val mailbox = Channel<EngineCommand>(Channel.UNLIMITED)
    private val eventChannel = Channel<PlayerEvent>(Channel.UNLIMITED)

    /** Serial off-actor worker for blocking TTS/radio loads; never a Lavaplayer callback thread. */
    private val syncLoads = Channel<LoadRequest>(Channel.UNLIMITED)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val listener = EngineListener()

    private val player: AudioPlayer = manager.createPlayer().apply {
        addListener(listener)
    }

    @Volatile
    private var state: EngineState = EngineState()

    /**
     * Invalidates every in-flight async/sync load when the queue is stopped or the engine closes.
     * A late result carries the epoch it started with, so it can never start playback after stop.
     */
    private val loadEpoch = AtomicLong(0)

    private val closed = AtomicBoolean(false)

    private var retryJob: Job? = null
    private var retryAttempt = 0

    private val subscriberClaimed = AtomicBoolean(false)

    /**
     * Single-subscriber event stream by design: one gateway adapter owns one engine. A second
     * concurrent or later collector fails instead of silently load-balancing events away from the
     * adapter.
     */
    override val events: Flow<PlayerEvent> = flow {
        check(subscriberClaimed.compareAndSet(false, true)) {
            "PlayerEngine.events supports exactly one subscriber per engine"
        }
        emitAll(eventChannel.receiveAsFlow())
    }

    init {
        scope.launch { consume() }
        scope.launch(Dispatchers.IO) { consumeSyncLoads() }
    }

    override suspend fun loadAndPlay(url: String, addToFront: Boolean) {
        submitAsyncLoad(LoadRequest(url, addToFront = addToFront))
    }

    override suspend fun loadAndPlayMultiple(tracks: List<String>) {
        // One ordering channel per call: Lavaplayer runs every load submitted with the same key
        // sequentially in submission order, so delayed providers cannot reorder the queue.
        val batch = BatchLoad()
        try {
            tracks.forEachIndexed { index, track ->
                submitAsyncLoad(
                    LoadRequest(
                        input = track,
                        addToFront = false,
                        order = index,
                        batch = batch,
                    )
                )
            }
        } catch (cancellation: CancellationException) {
            batch.cancelled.set(true)
            throw cancellation
        }
    }

    override suspend fun loadAndPlayMultipleWithCustomName(tracks: List<String>, customName: String) {
        val batch = BatchLoad()
        try {
            tracks.forEachIndexed { index, track ->
                submitAsyncLoad(
                    LoadRequest(
                        input = track,
                        addToFront = false,
                        customName = customName,
                        order = index,
                        batch = batch,
                    )
                )
            }
        } catch (cancellation: CancellationException) {
            batch.cancelled.set(true)
            throw cancellation
        }
    }

    /**
     * Delivers an async load request. If the caller is cancelled while the request is being
     * delivered, the request is marked cancelled; the actor and its callbacks then discard it.
     */
    private suspend fun submitAsyncLoad(request: LoadRequest) {
        try {
            mailbox.send(EngineCommand.Load(request))
        } catch (cancellation: CancellationException) {
            request.cancelled.set(true)
            throw cancellation
        }
    }

    override suspend fun loadAndPlayTts(message: String) {
        val acknowledgement = CompletableDeferred<Unit>()
        val request = LoadRequest(
            input = message,
            addToFront = false,
            acknowledgement = acknowledgement,
        )
        acknowledgement.invalidateRequestOnCancellation(request)
        submitSyncLoad(request)
    }

    override suspend fun playRadio(radioName: String, radioUrl: String, customFavicon: String) {
        val acknowledgement = CompletableDeferred<Unit>()
        val request = LoadRequest(
            input = radioUrl,
            addToFront = false,
            customName = radioName,
            customFavicon = customFavicon,
            acknowledgement = acknowledgement,
        )
        acknowledgement.invalidateRequestOnCancellation(request)
        submitSyncLoad(request)
    }

    /**
     * Sends the request and waits for its completion. If the calling coroutine is cancelled at
     * either step, the shared request is marked cancelled before the exception propagates.
     * Cancelling `await()` alone does not complete the deferred, so its completion handler cannot
     * be relied on for caller cancellation.
     */
    private suspend fun submitSyncLoad(request: LoadRequest) {
        try {
            mailbox.send(EngineCommand.LoadSync(request))
            request.acknowledgement?.await()
        } catch (cancellation: CancellationException) {
            request.cancelled.set(true)
            throw cancellation
        }
    }

    /**
     * Also handles a deferred completed with cancellation by another path. The flag is shared by
     * every epoch-scoped copy of the request.
     */
    private fun CompletableDeferred<Unit>.invalidateRequestOnCancellation(request: LoadRequest) {
        invokeOnCompletion { cause ->
            if (cause is CancellationException) {
                request.cancelled.set(true)
            }
        }
    }

    override suspend fun pause(): Boolean {
        val acknowledgement = CompletableDeferred<Boolean>()
        mailbox.send(EngineCommand.Pause(acknowledgement))
        return acknowledgement.await()
    }

    override suspend fun resume(): Boolean {
        val acknowledgement = CompletableDeferred<Boolean>()
        mailbox.send(EngineCommand.Resume(acknowledgement))
        return acknowledgement.await()
    }

    override suspend fun skip(): Boolean {
        val acknowledgement = CompletableDeferred<Boolean>()
        mailbox.send(EngineCommand.Skip(acknowledgement))
        return acknowledgement.await()
    }

    override suspend fun stop(): Boolean {
        val acknowledgement = CompletableDeferred<Boolean>()
        mailbox.send(EngineCommand.Stop(acknowledgement))
        return acknowledgement.await()
    }

    override suspend fun shuffle(): Boolean {
        val acknowledgement = CompletableDeferred<Boolean>()
        mailbox.send(EngineCommand.Shuffle(acknowledgement))
        return acknowledgement.await()
    }

    override suspend fun moveTrackToNext(searchTerm: String): PlayerTrack? {
        val acknowledgement = CompletableDeferred<PlayerTrack?>()
        mailbox.send(EngineCommand.MoveTrack(searchTerm, acknowledgement))
        return acknowledgement.await()
    }

    override fun snapshot(): PlayerSnapshot {
        val current = state
        return PlayerSnapshot(
            current = current.current?.toSnapshot(),
            queue = current.queue.map { it.toSnapshot() },
            paused = current.paused,
            hasActiveTrack = current.current != null,
        )
    }

    override fun isPaused(): Boolean = state.paused

    override fun isQueueEmpty(): Boolean = state.queue.isEmpty()

    override fun audioStream(): ContinuousPlayerAudioStream =
        LavaPlayerAudioStream(player) { state.terminated || closed.get() }.apply {
            onFailure = { error -> mailbox.trySend(EngineCommand.PlaybackFailed(error)) }
        }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        loadEpoch.incrementAndGet()
        drainSyncLoads()
        syncLoads.close()
        state = state.copy(terminated = true, current = null, queue = emptyList())
        retryJob?.cancel()
        retryJob = null
        mailbox.trySend(EngineCommand.Shutdown)
        drainMailbox()
        mailbox.close()
        player.destroy()
        if (ownsManager) {
            manager.shutdown()
        }
        scope.cancel()
        eventChannel.close()
    }

    private suspend fun consumeSyncLoads() {
        for (request in syncLoads) {
            loadSyncOffActor(request)
        }
    }

    private fun loadSyncOffActor(request: LoadRequest) {
        if (request.cancelled.get() || closed.get() || request.epoch != loadEpoch.get()) {
            request.acknowledgement?.complete(Unit)
            return
        }

        val item = try {
            manager.loadItemSync(request.input)
        } catch (cancellation: CancellationException) {
            request.acknowledgement?.complete(Unit)
            throw cancellation
        } catch (error: Throwable) {
            LOG.warn("The synchronous player load failed", error)
            if (!closed.get() && request.epoch == loadEpoch.get()) {
                mailbox.trySend(EngineCommand.SyncLoadFailed(request, error))
            } else {
                request.acknowledgement?.complete(Unit)
            }
            return
        }

        // TTS and radio historically required a single AudioTrack and silently ignored anything
        // else; preserve that instead of starting a playlist the user never expected here.
        val stale = request.cancelled.get() || closed.get() || request.epoch != loadEpoch.get()
        if (item !is AudioTrack || stale) {
            request.acknowledgement?.complete(Unit)
            return
        }
        if (!mailbox.trySend(EngineCommand.SyncLoaded(request, item)).isSuccess) {
            request.acknowledgement?.complete(Unit)
        }
    }

    private fun drainSyncLoads() {
        while (true) {
            val pending = syncLoads.tryReceive().getOrNull() ?: return
            pending.acknowledgement?.complete(Unit)
        }
    }

    private fun drainMailbox() {
        while (true) {
            val pending = mailbox.tryReceive().getOrNull() ?: return
            pending.failAcknowledgement()
        }
    }

    private suspend fun consume() {
        for (command in mailbox) {
            try {
                handle(command)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                LOG.error("The player mailbox command failed", error)
                command.failAcknowledgement()
            }
        }
    }

    private suspend fun handle(command: EngineCommand) {
        when (command) {
            is EngineCommand.Load -> load(command.request)

            is EngineCommand.LoadSync -> startSyncLoad(command.request)

            is EngineCommand.Pause -> {
                player.isPaused = true
                publish(state.copy(paused = true))
                command.acknowledgement.complete(true)
            }

            is EngineCommand.Resume -> {
                player.isPaused = false
                publish(state.copy(paused = false))
                command.acknowledgement.complete(true)
            }

            is EngineCommand.Skip -> {
                val current = state.current
                if (current == null) {
                    command.acknowledgement.complete(false)
                } else {
                    player.stopTrack()
                    // Lavaplayer dispatches TrackEnded synchronously to the listener, which only
                    // enqueues it. Advance now so skip has the same immediate effect the Kord
                    // adapter had; the queued duplicate is ignored because the current track
                    // changed.
                    handleTrackEnded(current.track, AudioTrackEndReason.STOPPED)
                    command.acknowledgement.complete(true)
                }
            }

            is EngineCommand.Stop -> {
                retryJob?.cancel()
                retryJob = null
                retryAttempt = 0
                loadEpoch.incrementAndGet()
                drainSyncLoads()
                state = state.copy(current = null, queue = emptyList(), terminated = true)
                player.stopTrack()
                eventChannel.trySend(PlayerEvent.Stopped)
                emitQueueChanged()
                command.acknowledgement.complete(true)
            }

            is EngineCommand.Shuffle -> {
                val shuffled = state.queue.toMutableList().apply { shuffle() }
                publish(state.copy(queue = shuffled))
                command.acknowledgement.complete(true)
            }

            is EngineCommand.MoveTrack -> command.acknowledgement.complete(
                moveTrackToFront(command.searchTerm)
            )

            is EngineCommand.ItemLoaded -> handleLoaded(command.request, command.item)

            is EngineCommand.SyncLoaded -> handleLoaded(command.request, command.item)

            is EngineCommand.SyncLoadFailed -> {
                if (!closed.get() && command.request.epoch == loadEpoch.get()) {
                    eventChannel.trySend(PlayerEvent.LoadFailed(command.error.message))
                }
                command.request.acknowledgement?.complete(Unit)
            }

            is EngineCommand.NoMatches -> {
                if (command.request.epoch == loadEpoch.get()) {
                    eventChannel.trySend(PlayerEvent.NoMatches(command.request.input))
                }
                command.request.acknowledgement?.complete(Unit)
            }

            is EngineCommand.ItemFailed -> {
                if (command.request.epoch == loadEpoch.get()) {
                    eventChannel.trySend(PlayerEvent.LoadFailed(command.error.message))
                }
                command.request.acknowledgement?.complete(Unit)
            }

            is EngineCommand.PlaybackFailed -> handlePlaybackFailed(command.error)

            is EngineCommand.TrackStarted -> handleTrackStarted(command.track)

            is EngineCommand.TrackEnded -> handleTrackEnded(command.track, command.reason)

            is EngineCommand.Retry -> handleRetry(command.track, command.attempt)

            EngineCommand.Shutdown -> Unit
        }
    }

    private fun load(request: LoadRequest) {
        if (request.isCancelled()) return
        val scoped = request.copy(epoch = loadEpoch.get())
        manager.loadItemOrdered(scoped.orderKey, scoped.input, LoadHandler(scoped))
    }

    private fun startSyncLoad(request: LoadRequest) {
        // The actor only queues the request; the actual blocking load runs on the dedicated IO
        // worker, so Pause/Skip/Stop and state transitions stay responsive.
        if (request.cancelled.get()) {
            request.acknowledgement?.complete(Unit)
            return
        }
        val scoped = request.copy(epoch = loadEpoch.get())
        if (!syncLoads.trySend(scoped).isSuccess) {
            request.acknowledgement?.complete(Unit)
        }
    }

    private fun handleLoaded(request: LoadRequest, item: AudioItem) {
        if (request.isCancelled() || closed.get() || request.epoch != loadEpoch.get()) {
            request.acknowledgement?.complete(Unit)
            return
        }
        when (item) {
            is AudioTrack -> {
                val queued = QueuedTrack(
                    track = item,
                    customName = request.customName,
                    customFavicon = request.customFavicon,
                )
                // Emit the "added" event before starting playback so adapters keep the previous
                // message-before-now-playing ordering.
                eventChannel.trySend(
                    PlayerEvent.TrackQueued(
                        track = queued.toSnapshot(),
                        addToFront = request.addToFront,
                        uri = item.info.uri,
                    )
                )
                enqueue(listOf(queued), request.addToFront)
            }

            is AudioPlaylist -> {
                val queued = item.tracks.map {
                    QueuedTrack(track = it, customName = request.customName, customFavicon = request.customFavicon)
                }
                eventChannel.trySend(
                    PlayerEvent.PlaylistQueued(
                        name = item.name,
                        trackCount = item.tracks.size,
                        playlistUrl = (item as? ExtendedAudioPlaylist)?.url,
                        addToFront = request.addToFront,
                    )
                )
                enqueue(queued, request.addToFront)
            }
        }
        emitQueueChanged()
        request.acknowledgement?.complete(Unit)
    }

    private fun enqueue(tracks: List<QueuedTrack>, addToFront: Boolean) {
        if (tracks.isEmpty()) return
        val queue = state.queue.toMutableList()
        if (addToFront) {
            queue.addAll(0, tracks)
        } else {
            queue.addAll(tracks)
        }
        state = state.copy(queue = queue, terminated = false)
        if (state.current == null) {
            playNext()
        }
    }

    private fun playNext() {
        val queue = state.queue.toMutableList()
        if (queue.isEmpty()) {
            state = state.copy(current = null, terminated = true)
            emitQueueChanged()
            return
        }
        val next = queue.removeAt(0)
        retryJob?.cancel()
        retryJob = null
        retryAttempt = 0
        state = state.copy(current = next, queue = queue, terminated = false)
        player.startTrack(next.track, false)
        emitQueueChanged()
    }

    private fun handlePlaybackFailed(error: Throwable) {
        LOG.warn("The audio provider failed while producing frames", error)
        eventChannel.trySend(PlayerEvent.PlaybackFailed(error.message))
        val current = state.current
        if (current != null && !state.terminated) {
            player.stopTrack()
            handleTrackEnded(current.track, AudioTrackEndReason.FINISHED)
        }
    }

    private fun handleTrackStarted(track: AudioTrack) {
        val current = state.current
        if (current == null || current.track !== track) return
        emitQueueChanged()
        if (retryAttempt == 0) {
            eventChannel.trySend(PlayerEvent.TrackStarted(current.toSnapshot()))
        }
    }

    private fun handleTrackEnded(track: AudioTrack?, endReason: AudioTrackEndReason?) {
        if (state.terminated) return
        val current = state.current
        if (current == null || (track != null && current.track !== track)) return

        if (endReason in RETRYABLE_END_REASONS && retryAttempt < MAX_RETRIES) {
            scheduleRetry(current.track)
            return
        }

        retryAttempt = 0
        val queueEmpty = state.queue.isEmpty()
        state = state.copy(current = null)
        eventChannel.trySend(PlayerEvent.TrackEnded(queueEmpty = queueEmpty))
        playNext()
    }

    private fun scheduleRetry(track: AudioTrack) {
        retryAttempt += 1
        val attempt = retryAttempt
        retryJob?.cancel()
        retryJob = scope.launch {
            delay(min(MAX_BACKOFF_MILLIS, FIRST_BACKOFF_MILLIS * attempt).milliseconds)
            mailbox.send(EngineCommand.Retry(track, attempt))
        }
    }

    private fun handleRetry(track: AudioTrack, attempt: Int) {
        if (state.terminated || attempt != retryAttempt) return
        val current = state.current ?: return
        val clone = track.makeClone()
        player.startTrack(clone, false)
        state = state.copy(current = current.copy(track = clone))
        emitQueueChanged()
    }

    private fun moveTrackToFront(searchTerm: String): PlayerTrack? {
        val normalized = searchTerm.lowercase()
        val queue = state.queue
        val exactIndex = queue.indexOfFirst { it.toSnapshot().displayName.lowercase() == normalized }
        val index = if (exactIndex != -1) {
            exactIndex
        } else {
            queue.indexOfFirst { it.toSnapshot().displayName.lowercase().contains(normalized) }
        }
        if (index == -1) return null

        val reordered = queue.toMutableList()
        val moved = reordered.removeAt(index)
        reordered.add(0, moved)
        publish(state.copy(queue = reordered))
        return moved.toSnapshot()
    }

    private fun publish(newState: EngineState) {
        state = newState
        emitQueueChanged()
    }

    private fun emitQueueChanged() {
        eventChannel.trySend(PlayerEvent.QueueChanged(snapshot()))
    }

    private fun QueuedTrack.toSnapshot(positionMillis: Long = track.position): PlayerTrack = PlayerTrack(
        identifier = track.identifier,
        title = track.info.title,
        author = track.info.author,
        durationMillis = track.info.length,
        uri = track.info.uri,
        artworkUrl = track.info.artworkUrl,
        isStream = track.info.isStream,
        sourceName = track.sourceManager.sourceName,
        customName = customName,
        customFavicon = customFavicon,
        positionMillis = positionMillis,
    )

    private inner class LoadHandler(
        private val request: LoadRequest,
    ) : AudioLoadResultHandler {

        override fun trackLoaded(track: AudioTrack) {
            mailbox.trySend(EngineCommand.ItemLoaded(request, track))
        }

        override fun playlistLoaded(playlist: AudioPlaylist) {
            mailbox.trySend(EngineCommand.ItemLoaded(request, playlist))
        }

        override fun noMatches() {
            mailbox.trySend(EngineCommand.NoMatches(request))
        }

        override fun loadFailed(exception: FriendlyException) {
            mailbox.trySend(EngineCommand.ItemFailed(request, exception))
        }
    }

    private inner class EngineListener : AudioEventAdapter() {

        override fun onTrackStart(player: AudioPlayer?, track: AudioTrack?) {
            track?.let { mailbox.trySend(EngineCommand.TrackStarted(it)) }
        }

        override fun onTrackEnd(player: AudioPlayer?, track: AudioTrack?, endReason: AudioTrackEndReason?) {
            mailbox.trySend(EngineCommand.TrackEnded(track, endReason))
        }
    }

    private data class QueuedTrack(
        val track: AudioTrack,
        val customName: String? = null,
        val customFavicon: String? = null,
    )

    private data class EngineState(
        val current: QueuedTrack? = null,
        val queue: List<QueuedTrack> = emptyList(),
        val paused: Boolean = false,
        val terminated: Boolean = true,
    )

    private data class LoadRequest(
        val input: String,
        val addToFront: Boolean,
        val customName: String? = null,
        val customFavicon: String? = null,
        val order: Int? = null,
        /** Shared identity for one multi-track call; keeps completion order deterministic. */
        val batch: BatchLoad? = null,
        val acknowledgement: CompletableDeferred<Unit>? = null,
        val epoch: Long = 0L,
        val cancelled: AtomicBoolean = AtomicBoolean(false),
    ) {
        val orderKey: Any get() = batch?.key ?: order ?: input

        fun isCancelled(): Boolean = cancelled.get() || batch?.cancelled?.get() == true
    }

    private class BatchLoad {
        val key: Any = Any()
        val cancelled = AtomicBoolean(false)
    }

    private sealed interface EngineCommand {

        /** Releases a waiting caller when the actor fails before acknowledging the command. */
        fun failAcknowledgement() = Unit

        data class Load(val request: LoadRequest) : EngineCommand

        data class LoadSync(val request: LoadRequest) : EngineCommand {
            override fun failAcknowledgement() {
                request.acknowledgement?.complete(Unit)
            }
        }

        data class Pause(val acknowledgement: CompletableDeferred<Boolean>) : EngineCommand {
            override fun failAcknowledgement() {
                acknowledgement.complete(false)
            }
        }

        data class Resume(val acknowledgement: CompletableDeferred<Boolean>) : EngineCommand {
            override fun failAcknowledgement() {
                acknowledgement.complete(false)
            }
        }

        data class Skip(val acknowledgement: CompletableDeferred<Boolean>) : EngineCommand {
            override fun failAcknowledgement() {
                acknowledgement.complete(false)
            }
        }

        data class Stop(val acknowledgement: CompletableDeferred<Boolean>) : EngineCommand {
            override fun failAcknowledgement() {
                acknowledgement.complete(false)
            }
        }

        data class Shuffle(val acknowledgement: CompletableDeferred<Boolean>) : EngineCommand {
            override fun failAcknowledgement() {
                acknowledgement.complete(false)
            }
        }

        data class MoveTrack(
            val searchTerm: String,
            val acknowledgement: CompletableDeferred<PlayerTrack?>,
        ) : EngineCommand {
            override fun failAcknowledgement() {
                acknowledgement.complete(null)
            }
        }

        data class ItemLoaded(val request: LoadRequest, val item: AudioItem) : EngineCommand {
            override fun failAcknowledgement() {
                request.acknowledgement?.complete(Unit)
            }
        }

        data class SyncLoaded(val request: LoadRequest, val item: AudioItem) : EngineCommand {
            override fun failAcknowledgement() {
                request.acknowledgement?.complete(Unit)
            }
        }

        data class SyncLoadFailed(val request: LoadRequest, val error: Throwable) : EngineCommand {
            override fun failAcknowledgement() {
                request.acknowledgement?.complete(Unit)
            }
        }

        data class NoMatches(val request: LoadRequest) : EngineCommand {
            override fun failAcknowledgement() {
                request.acknowledgement?.complete(Unit)
            }
        }

        data class ItemFailed(val request: LoadRequest, val error: FriendlyException) : EngineCommand {
            override fun failAcknowledgement() {
                request.acknowledgement?.complete(Unit)
            }
        }

        data class PlaybackFailed(val error: Throwable) : EngineCommand

        data class TrackStarted(val track: AudioTrack) : EngineCommand

        data class TrackEnded(val track: AudioTrack?, val reason: AudioTrackEndReason?) : EngineCommand

        data class Retry(val track: AudioTrack, val attempt: Int) : EngineCommand

        data object Shutdown : EngineCommand
    }
}

private val RETRYABLE_END_REASONS = setOf(
    AudioTrackEndReason.LOAD_FAILED,
    AudioTrackEndReason.CLEANUP,
)
