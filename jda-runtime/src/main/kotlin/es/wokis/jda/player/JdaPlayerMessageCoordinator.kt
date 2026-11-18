package es.wokis.jda.player

import es.wokis.domain.locale.LocaleCode
import es.wokis.domain.model.ChannelId
import es.wokis.domain.model.GuildId
import es.wokis.localization.GuildLocalizationService
import es.wokis.localization.PlayerMessageKeys
import es.wokis.message.MessageView
import es.wokis.player.PlayerMessageId
import es.wokis.player.engine.PlayerEngine
import es.wokis.player.engine.event.PlayerEvent
import es.wokis.player.engine.model.PlayerSnapshot
import es.wokis.player.message.PlayerMessageViewFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicReference

private const val LEAVE_DELAY_MILLIS = 10_000L
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
 * player command; the embed lives in `#player`. Nothing here may crash the runtime, so every JDA
 * failure degrades to a logged warning and, for the embed, to the invoking channel.
 */
internal class JdaPlayerMessageCoordinator(
    private val guild: Guild,
    private val engine: PlayerEngine,
    private val adapter: JdaGuildPlayerVoiceAdapter,
    private val localizationService: GuildLocalizationService,
    private val channelService: JdaPlayerChannelService,
    private val viewFactory: PlayerMessageViewFactory,
    scope: CoroutineScope,
    private val onLeave: suspend () -> Unit,
) : AutoCloseable {

    private val coordinatorJob = SupervisorJob(scope.coroutineContext[Job])
    private val coordinatorScope = CoroutineScope(scope.coroutineContext + coordinatorJob)
    private val port = JdaPlayerMessagePort(guild.jda)
    private val eventQueue = Channel<PlayerEvent>(Channel.UNLIMITED)
    private val leaveTimer = AtomicReference<Job?>(null)

    @Volatile
    private var commandChannelId: ChannelId? = null

    @Volatile
    private var guildId: GuildId = GuildId(guild.idLong.toULong())

    @Volatile
    private var guildLocale: LocaleCode? = null

    @Volatile
    private var recordedVoiceChannelName: String = ""

    @Volatile
    private var voiceChannelId: ChannelId? = null

    private var playerChannelId: ChannelId? = null
    private var playerMessageId: PlayerMessageId? = null

    init {
        coordinatorScope.launch {
            try {
                adapter.events.collect { eventQueue.trySend(it) }
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
    }

    /** Remembers where the next event-driven message must be posted. */
    fun recordCommand(channelId: ChannelId, guildId: GuildId, guildLocale: LocaleCode?, channelName: String) {
        commandChannelId = channelId
        this.guildId = guildId
        this.guildLocale = guildLocale
        recordedVoiceChannelName = channelName
    }

    /** Loads [input] and joins voice once it is active; failures surface as chat messages. */
    fun startPlayback(channel: VoiceChannel, input: String) {
        voiceChannelId = ChannelId(channel.idLong.toULong())
        cancelLeaveTimer()
        coordinatorScope.launch { runPlayback(channel, input) }
    }

    override fun close() {
        cancelLeaveTimer()
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
                is PlayerEvent.QueueChanged -> updatePlayerView(event.snapshot)
                PlayerEvent.Stopped -> {
                    cancelLeaveTimer()
                    updatePlayerView(engine.snapshot())
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
            val messageId = port.send(
                channelId,
                text(PlayerMessageKeys.FOUND_TRACK_LIST, event.name, event.trackCount),
            )
            port.edit(
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
        val snapshot = engine.snapshot()
        // Kord parity: the now-playing announcement exists only until the player message is created.
        if (playerMessageId == null) {
            announce(
                text(
                    PlayerMessageKeys.NOW_PLAYING,
                    snapshot.current?.displayName.orEmpty(),
                    resolveVoiceChannelName(),
                ),
            )
        }
        ensurePlayerView(snapshot)
    }

    private suspend fun onTrackEnded(event: PlayerEvent.TrackEnded) {
        if (event.queueEmpty) scheduleLeave()
        updatePlayerView(engine.snapshot())
    }

    private suspend fun announce(content: String) {
        val channelId = commandChannelId ?: return
        try {
            port.send(channelId, content)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            LOG.warn("Could not announce a player message", error)
        }
    }

    /** First embed for this guild: reuses or creates `#player`, clears it and posts the message. */
    private suspend fun ensurePlayerView(snapshot: PlayerSnapshot) {
        if (playerChannelId != null && playerMessageId != null) {
            updatePlayerView(snapshot)
            return
        }
        // View construction shares the guarded region: any failure falls back to the invoking channel.
        try {
            val view = viewFactory.createPlayerView(guild.name, snapshot, guildId, guildLocale)
            val result = channelService.setUp(guild, view)
            if (result == null) {
                fallbackToCommandChannel(view)
                return
            }
            playerChannelId = result.channelId
            playerMessageId = result.messageId
            announce(
                text(
                    if (result.isNewChannel) {
                        PlayerMessageKeys.PLAYER_CHANNEL_CREATED
                    } else {
                        PlayerMessageKeys.PLAYER_SHOWN_ON_EXISTING_CHANNEL
                    },
                    result.channelId.value,
                ),
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            LOG.warn("Could not set up the player message; falling back to the invoking channel", error)
            fallbackToCommandChannel(view = null)
        }
    }

    private suspend fun updatePlayerView(snapshot: PlayerSnapshot) {
        val channelId = playerChannelId ?: return
        val messageId = playerMessageId ?: return
        try {
            port.edit(channelId, messageId, viewFactory.createPlayerView(guild.name, snapshot, guildId, guildLocale))
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            LOG.warn("Could not update the player message; #player will be rebuilt on the next track", error)
            playerChannelId = null
            playerMessageId = null
        }
    }

    /** Kord parity when `#player` is missing or unwritable: warn and show the player in the command channel. */
    private suspend fun fallbackToCommandChannel(view: MessageView?) {
        val channelId = commandChannelId ?: return
        val content = text(PlayerMessageKeys.PLAYER_CHANNEL_CREATION_FAILED)
        try {
            port.send(channelId, view?.copy(content = content) ?: MessageView(content = content))
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

    private suspend fun runPlayback(channel: VoiceChannel, input: String) {
        val baseRevision = adapter.presentation.value.revision
        try {
            engine.loadAndPlay(input)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            LOG.warn("The player load failed before an event was published", error)
            return
        }
        // A track was already playing: the new input is queued, so there is no start to wait for.
        if (adapter.presentation.value.activeTrack) {
            connect(channel)
            return
        }
        when (awaitPlaybackStart(baseRevision)) {
            JdaPlayerPlaybackStart.TimedOut -> announce(text(PlayerMessageKeys.PLAYER_LOAD_TIMEOUT))
            // A rejected load or a failed playback already produced its own event message.
            is JdaPlayerPlaybackStart.Failed -> Unit
            JdaPlayerPlaybackStart.Started -> connect(channel)
        }
    }

    private suspend fun awaitPlaybackStart(baseRevision: Long): JdaPlayerPlaybackStart {
        val state = withTimeoutOrNull(LOAD_START_TIMEOUT_MILLIS) {
            adapter.presentation.first { presentation ->
                presentation.revision > baseRevision &&
                    (presentation.activeTrack || presentation.loadFailure != null)
            }
        }
        if (state == null) return JdaPlayerPlaybackStart.TimedOut
        val failure = state.loadFailure
        return if (failure == null) JdaPlayerPlaybackStart.Started else JdaPlayerPlaybackStart.Failed(failure)
    }

    private suspend fun connect(channel: VoiceChannel) {
        try {
            if (!adapter.connect(channel)) announce(text(PlayerMessageKeys.PLAYER_VOICE_SESSION_REFUSED))
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            LOG.warn("The player voice connection failed", error)
            announce(text(PlayerMessageKeys.PLAYER_VOICE_CONNECTION_FAILED))
        }
    }

    /** Kord reads the connected channel's name at track start; the recorded name is only a fallback. */
    private fun resolveVoiceChannelName(): String =
        voiceChannelId
            ?.takeIf { it.value <= Long.MAX_VALUE.toULong() }
            ?.let { guild.jda.getVoiceChannelById(it.value.toLong())?.name }
            ?: recordedVoiceChannelName

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
        private val LOG = LoggerFactory.getLogger(JdaPlayerMessageCoordinator::class.java)
    }
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
