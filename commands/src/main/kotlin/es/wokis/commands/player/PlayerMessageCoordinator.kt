package es.wokis.commands.player

import es.wokis.domain.locale.LocaleCode
import es.wokis.domain.model.ChannelId
import es.wokis.domain.model.GuildId
import es.wokis.localization.GuildLocalizationService
import es.wokis.localization.PlayerMessageKeys
import es.wokis.message.MessageView
import es.wokis.player.PlayerMessageId
import es.wokis.player.PlayerMessagePermanentFailure
import es.wokis.player.PlayerMessagePort
import es.wokis.player.engine.PlayerEngine
import es.wokis.player.engine.event.PlayerEvent
import es.wokis.player.message.PlayerMessageViewFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicReference

private const val LEAVE_DELAY_MILLIS = 10_000L
private const val EMBED_COLLECTOR_RESTART_DELAY_MILLIS = 1_000L
private const val LOAD_START_TIMEOUT_MILLIS = 30_000L
private const val EMOJI_PATTERN =
    "(\\p{IsEmoji_Presentation}|\\p{IsEmoji_Modifier}|\\p{IsEmoji_Modifier_Base}|" +
        "\\p{IsEmoji_Component}|\\p{IsExtended_Pictographic})+"
private const val URL_PATTERN = "https?://[^\\s]+"
private val PROBLEMATIC_MARKDOWN_PATTERN = Regex("$EMOJI_PATTERN|$URL_PATTERN")

/**
 * Kord-parity chat and player-embed behavior for one guild.
 *
 * Owns the only collector of the adapter's event fan-out: events are queued and handled serially so
 * REST latency never stalls the engine's event loop. Announcements go to the channel of the last
 * player command; the embed lives in `#player`. Nothing here may crash the runtime, so every failure
 * degrades to a logged warning and, for the embed, to the invoking channel. `guildName` is read at
 * render time so a renamed guild renders its current name.
 */
class PlayerMessageCoordinator(
    private val initialGuildId: GuildId,
    private val guildName: () -> String,
    private val engine: PlayerEngine,
    private val eventsPort: PlayerEventsPort,
    private val localizationService: GuildLocalizationService,
    private val channelPort: PlayerChannelPort,
    private val messagePort: PlayerMessagePort,
    private val voiceTargets: PlayerVoiceTargetPort,
    private val viewFactory: PlayerMessageViewFactory,
    scope: CoroutineScope,
    private val onLeave: suspend () -> Unit,
) : AutoCloseable {

    private val coordinatorJob = SupervisorJob(scope.coroutineContext[Job])
    private val coordinatorScope = CoroutineScope(scope.coroutineContext + coordinatorJob)
    private val eventQueue = Channel<PlayerEvent>(Channel.UNLIMITED)

    /**
     * Latest-wins signal for the player embed. Chat messages stay on [eventQueue]; a coalesced
     * signal only asks the embed loop to render the current state once, so a slow REST PATCH never
     * blocks announcements and a burst of events collapses into a single edit.
     */
    private val embedSignal = Channel<Unit>(Channel.CONFLATED)
    private val leaveTimer = AtomicReference<Job?>(null)

    /** Set before the signal closes so the embed loop never restarts on a normal shutdown. */
    @Volatile
    private var embedCollectorStopped: Boolean = false

    @Volatile
    private var commandChannelId: ChannelId? = null

    @Volatile
    private var guildId: GuildId = initialGuildId

    @Volatile
    private var guildLocale: LocaleCode? = null

    @Volatile
    private var recordedVoiceChannelName: String = ""

    @Volatile
    private var voiceChannelId: ChannelId? = null

    // Both are written by the command coroutine (`/player`) and read by the event coroutine.
    @Volatile
    private var playerChannelId: ChannelId? = null

    @Volatile
    private var playerMessageId: PlayerMessageId? = null

    init {
        coordinatorScope.launch {
            try {
                eventsPort.events.collect { eventQueue.trySend(it) }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                LOG.warn("The player event fan-out failed", error)
            }
        }
        coordinatorScope.launch {
            for (event in eventQueue) {
                handleEvent(event)
            }
        }
        coordinatorScope.launch {
            // Renders the live snapshot once per coalesced signal; separate from the chat loop so a
            // rate-limited embed edit can never stall the per-event announcements. The collector is
            // the only writer of the tracked message, so a stray CancellationException must not
            // retire it silently: every attempt runs as a supervised child and restarts while the
            // coordinator is alive, loudly and with a delay so a cancelling port cannot spin.
            while (currentCoroutineContext().isActive) {
                try {
                    coordinatorScope.launch {
                        // `receiveAsFlow` (not `consumeAsFlow`): a failing or cancelled collector must
                        // not close the shared signal, or the restart below could never receive again.
                        embedSignal.receiveAsFlow().collect { updatePlayerView() }
                    }.join()
                } catch (_: CancellationException) {
                    // `join()` resumes normally on child failure or cancellation; this catch only
                    // observes cancellation of THIS waiting coroutine (teardown or parent cancel),
                    // where the guard below returns silently. Teardown insurance, not child handling.
                    if (!coordinatorJob.isActive || embedCollectorStopped) return@launch
                }
                if (!coordinatorJob.isActive || embedCollectorStopped) return@launch
                LOG.warn(
                    "The player embed collector stopped while the coordinator is still running; " +
                        "embed updates are disabled until it restarts",
                )
                delay(EMBED_COLLECTOR_RESTART_DELAY_MILLIS)
            }
        }
    }

    /** Remembers where the next event-driven message must be posted. */
    fun recordCommand(
        channelId: ChannelId,
        guildId: GuildId,
        guildLocale: LocaleCode?,
        channelName: String? = null,
    ) {
        commandChannelId = channelId
        this.guildId = guildId
        this.guildLocale = guildLocale
        if (channelName != null) recordedVoiceChannelName = channelName
    }

    /** Loads [input] and joins voice once it is active; failures surface as chat messages. */
    fun startPlayback(target: PlayerVoiceTarget, input: String) {
        voiceChannelId = target.channelId
        cancelLeaveTimer()
        coordinatorScope.launch { runPlayback(target, input) }
    }

    /**
     * Joins [target] before a content load that does not go through [startPlayback].
     *
     * Kord parity: the connect is attempted before the load starts, records the connected channel
     * for later now-playing announcements and cancels a pending leave. A refused or failed
     * connection is not fatal — it is announced with the same non-fatal warning `/play` emits and
     * the caller's load still runs.
     */
    suspend fun connectVoice(target: PlayerVoiceTarget) {
        voiceChannelId = target.channelId
        cancelLeaveTimer()
        connect(target)
    }

    override fun close() {
        cancelLeaveTimer()
        embedCollectorStopped = true
        embedSignal.close()
        eventQueue.close()
        coordinatorJob.cancel()
    }

    private suspend fun handleEvent(event: PlayerEvent) {
        try {
            when (event) {
                is PlayerEvent.TrackQueued -> onTrackQueued(event)
                is PlayerEvent.PlaylistQueued -> onPlaylistQueued(event)
                is PlayerEvent.NoMatches -> announce(text(PlayerMessageKeys.NO_MATCHES, event.input))
                // Kord echoes the reason, but provider text can leak diagnostics: report a category only.
                is PlayerEvent.LoadFailed ->
                    announce(text(PlayerMessageKeys.LOAD_FAILED, text(PlayerMessageKeys.PLAYER_LOAD_REJECTED)))

                is PlayerEvent.PlaybackFailed ->
                    announce(text(PlayerMessageKeys.LOAD_FAILED, text(PlayerMessageKeys.PLAYER_PLAYBACK_FAILED)))

                is PlayerEvent.TrackStarted -> onTrackStarted()
                is PlayerEvent.TrackEnded -> onTrackEnded(event)
                is PlayerEvent.QueueChanged -> embedSignal.trySend(Unit)
                PlayerEvent.Stopped -> {
                    cancelLeaveTimer()
                    embedSignal.trySend(Unit)
                }
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            LOG.warn("The player message for event {} failed", event.javaClass.simpleName, error)
        }
    }

    private suspend fun onTrackQueued(event: PlayerEvent.TrackQueued) {
        cancelLeaveTimer()
        announce(queuedText(event, localizationService, guildId, guildLocale))
    }

    private suspend fun onPlaylistQueued(event: PlayerEvent.PlaylistQueued) {
        cancelLeaveTimer()
        val channelId = commandChannelId ?: return
        // Kord sends `found_track_list` first and then edits it into the added-songs text.
        try {
            val messageId = messagePort.send(
                channelId,
                text(PlayerMessageKeys.FOUND_TRACK_LIST, event.name, event.trackCount),
            )
            messagePort.edit(
                channelId = channelId,
                messageId = messageId,
                content = addedSongsText(event, localizationService, guildId, guildLocale),
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            LOG.warn("Could not announce the playlist in the player channel", error)
        }
    }

    private suspend fun onTrackStarted() {
        cancelLeaveTimer()
        // Kord parity: only `/player` creates the embed; a track start announces or edits an existing one.
        if (playerMessageId == null) {
            val snapshot = engine.snapshot()
            val currentName = snapshot.current?.displayName.orEmpty()
            val channelName = resolveVoiceChannelName(voiceChannelId, recordedVoiceChannelName, voiceTargets)
            announce(text(PlayerMessageKeys.NOW_PLAYING, currentName, channelName))
            return
        }
        embedSignal.trySend(Unit)
    }

    private suspend fun onTrackEnded(event: PlayerEvent.TrackEnded) {
        if (event.queueEmpty) scheduleLeave()
        embedSignal.trySend(Unit)
    }

    private suspend fun announce(content: String) {
        val channelId = commandChannelId ?: return
        try {
            messagePort.send(channelId, content)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            LOG.warn("Could not announce a player message", error)
        }
    }

    /**
     * Kord parity: only `/player` sets `#player` up (reuse or create it, clear its backlog and post
     * the embed for the current snapshot); returns the status line for the command reply.
     */
    suspend fun ensurePlayerView(): String = try {
        val snapshot = engine.snapshot()
        val view = viewFactory.createPlayerView(guildName(), snapshot, guildId, guildLocale)
        when (val result = channelPort.setUp(view)) {
            PlayerChannelSetup.Failed -> {
                fallbackToCommandChannel(view)
                text(PlayerMessageKeys.PLAYER_CHANNEL_CREATION_FAILED)
            }

            is PlayerChannelSetup.Created -> {
                playerChannelId = result.channelId
                playerMessageId = result.messageId
                text(
                    PlayerMessageKeys.PLAYER_CHANNEL_CREATED,
                    result.channelId.value,
                )
            }

            is PlayerChannelSetup.Reused -> {
                playerChannelId = result.channelId
                playerMessageId = result.messageId
                text(
                    PlayerMessageKeys.PLAYER_SHOWN_ON_EXISTING_CHANNEL,
                    result.channelId.value,
                )
            }
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Exception) {
        LOG.warn("Could not set up the player message; falling back to the invoking channel", error)
        fallbackToCommandChannel(view = null)
        text(PlayerMessageKeys.PLAYER_CHANNEL_CREATION_FAILED)
    }

    /** Builds the view for the current snapshot without posting or tracking a message. */
    suspend fun playerView(guildLocale: LocaleCode?): MessageView =
        viewFactory.createPlayerView(guildName(), engine.snapshot(), guildId, guildLocale)

    /**
     * Renders the live snapshot and edits the tracked player message once.
     *
     * Only a [PlayerMessagePermanentFailure] drops the handle so the next `/player` rebuilds it;
     * transient failures keep the ids and let the next coalesced signal retry.
     */
    private suspend fun updatePlayerView() {
        val channelId = playerChannelId ?: return
        val messageId = playerMessageId ?: return
        try {
            val view = viewFactory.createPlayerView(guildName(), engine.snapshot(), guildId, guildLocale)
            messagePort.edit(channelId, messageId, view)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (permanent: PlayerMessagePermanentFailure) {
            LOG.warn("The player message was permanently lost; the next /player rebuilds it", permanent)
            // Drop only the handle this edit was for; `/player` may have rebuilt a newer one meanwhile.
            if (playerMessageId == messageId) {
                playerChannelId = null
                playerMessageId = null
            }
        } catch (error: Exception) {
            LOG.warn("Could not update the player message; the next coalesced update retries", error)
        }
    }

    /** Kord parity when `#player` is missing or unwritable: warn and show the player in the command channel. */
    private suspend fun fallbackToCommandChannel(view: MessageView?) {
        val channelId = commandChannelId ?: return
        val content = text(PlayerMessageKeys.PLAYER_CHANNEL_CREATION_FAILED)
        try {
            messagePort.send(channelId, view?.copy(content = content) ?: MessageView(content = content))
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            LOG.warn("Could not show the player in the invoking channel", error)
        }
    }

    private fun scheduleLeave() {
        val job = coordinatorScope.launch(start = CoroutineStart.LAZY) {
            delay(LEAVE_DELAY_MILLIS)
            val snapshot = engine.snapshot()
            if (snapshot.hasActiveTrack || snapshot.queue.isNotEmpty()) return@launch
            try {
                onLeave()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                LOG.warn("Could not leave the voice channel after the idle timeout", error)
            }
        }
        // Install the timer before starting it so a concurrent cancel can never miss a live job.
        leaveTimer.getAndSet(job)?.cancel()
        job.start()
    }

    private fun cancelLeaveTimer() {
        leaveTimer.getAndSet(null)?.cancel()
    }

    private suspend fun runPlayback(target: PlayerVoiceTarget, input: String) {
        val baseRevision = eventsPort.playbackState.value.revision
        try {
            engine.loadAndPlay(input)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            LOG.warn("The player load failed before an event was published", error)
            return
        }
        // A track was already playing: the new input is queued, so there is no start to wait for.
        if (eventsPort.playbackState.value.activeTrack) {
            connect(target)
            return
        }
        when (awaitPlaybackStart(eventsPort, baseRevision)) {
            PlaybackStart.TimedOut -> announce(text(PlayerMessageKeys.PLAYER_LOAD_TIMEOUT))
            // A rejected load or a failed playback already produced its own event message.
            PlaybackStart.Failed -> Unit
            PlaybackStart.Started -> connect(target)
        }
    }

    private suspend fun connect(target: PlayerVoiceTarget) {
        try {
            if (!eventsPort.connect(target)) announce(text(PlayerMessageKeys.PLAYER_VOICE_SESSION_REFUSED))
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            LOG.warn("The player voice connection failed", error)
            announce(text(PlayerMessageKeys.PLAYER_VOICE_CONNECTION_FAILED))
        }
    }

    private suspend fun text(key: String, vararg arguments: Any): String =
        if (arguments.isEmpty()) {
            localizationService.getString(key = key, guildId = guildId, guildDefaultLocale = guildLocale)
        } else {
            localizationService.getStringFormat(
                key = key,
                guildId = guildId,
                guildDefaultLocale = guildLocale,
                arguments = arguments,
            )
        }

    private companion object {
        private val LOG = LoggerFactory.getLogger(PlayerMessageCoordinator::class.java)
    }
}

/** Kord reads the connected channel's name at track start; the recorded name is only a fallback. */
private suspend fun resolveVoiceChannelName(
    voiceChannelId: ChannelId?,
    recordedName: String,
    voiceTargets: PlayerVoiceTargetPort,
): String = voiceChannelId?.let { voiceTargets.channelName(it) } ?: recordedName

/** Waits for a later revision that reports a track or a load failure; times out silently. */
private suspend fun awaitPlaybackStart(eventsPort: PlayerEventsPort, baseRevision: Long): PlaybackStart {
    val state = withTimeoutOrNull(LOAD_START_TIMEOUT_MILLIS) {
        eventsPort.playbackState.first { presentation ->
            presentation.revision > baseRevision &&
                (presentation.activeTrack || presentation.loadFailed)
        }
    }
    if (state == null) return PlaybackStart.TimedOut
    return if (state.loadFailed) PlaybackStart.Failed else PlaybackStart.Started
}

/** Outcome of waiting for the adapter to report a track or a load failure. */
private sealed interface PlaybackStart {

    data object Started : PlaybackStart

    data object Failed : PlaybackStart

    data object TimedOut : PlaybackStart
}

private suspend fun queuedText(
    event: PlayerEvent.TrackQueued,
    localizationService: GuildLocalizationService,
    guildId: GuildId,
    guildLocale: LocaleCode?,
): String {
    val displayName = event.track.displayName
    val uri = event.uri
    return if (uri != null && uri.isPlayableUrl()) {
        localizationService.getStringFormat(
            key = if (event.addToFront) {
                PlayerMessageKeys.NEXT_ADDED_TO_QUEUE_WITH_LINK
            } else {
                PlayerMessageKeys.ADDED_TRACK_TO_QUEUE_WITH_LINK
            },
            guildId = guildId,
            guildDefaultLocale = guildLocale,
            arguments = arrayOf(displayName.toSanitizedMarkdownLink(uri)),
        )
    } else {
        localizationService.getStringFormat(
            key = if (event.addToFront) {
                PlayerMessageKeys.NEXT_ADDED_TO_QUEUE
            } else {
                PlayerMessageKeys.ADDED_TRACK_TO_QUEUE
            },
            guildId = guildId,
            guildDefaultLocale = guildLocale,
            arguments = arrayOf(displayName),
        )
    }
}

private suspend fun addedSongsText(
    event: PlayerEvent.PlaylistQueued,
    localizationService: GuildLocalizationService,
    guildId: GuildId,
    guildLocale: LocaleCode?,
): String {
    val playlistUrl = event.playlistUrl
    return if (playlistUrl != null && playlistUrl.isPlayableUrl()) {
        localizationService.getStringFormat(
            key = if (event.addToFront) {
                PlayerMessageKeys.NEXT_ADDED_SONGS_TO_QUEUE_WITH_LINK
            } else {
                PlayerMessageKeys.ADDED_SONGS_TO_QUEUE_WITH_LINK
            },
            guildId = guildId,
            guildDefaultLocale = guildLocale,
            arguments = arrayOf<Any>(event.name.toSanitizedMarkdownLink(playlistUrl), event.trackCount),
        )
    } else {
        localizationService.getStringFormat(
            key = if (event.addToFront) {
                PlayerMessageKeys.NEXT_ADDED_SONGS_TO_QUEUE
            } else {
                PlayerMessageKeys.ADDED_SONGS_TO_QUEUE
            },
            guildId = guildId,
            guildDefaultLocale = guildLocale,
            arguments = arrayOf<Any>(event.name, event.trackCount),
        )
    }
}

private fun String.isPlayableUrl(): Boolean = startsWith("http://") || startsWith("https://")

/**
 * Kord parity: emojis and raw URLs break Discord's markdown link, so only plain text is wrapped and
 * the excluded fragments are appended verbatim. The URL is the track/playlist link, never a provider
 * token.
 */
private fun String.toSanitizedMarkdownLink(url: String): String = buildString {
    var lastEnd = 0
    PROBLEMATIC_MARKDOWN_PATTERN.findAll(this@toSanitizedMarkdownLink).forEach { match ->
        val textBefore = this@toSanitizedMarkdownLink.substring(lastEnd, match.range.first)
        if (textBefore.isNotEmpty()) {
            append("[$textBefore]($url)")
        }
        append(match.value)
        lastEnd = match.range.last + 1
    }
    val remainingText = this@toSanitizedMarkdownLink.substring(lastEnd)
    if (remainingText.isNotEmpty()) {
        append("[$remainingText]($url)")
    }
}
