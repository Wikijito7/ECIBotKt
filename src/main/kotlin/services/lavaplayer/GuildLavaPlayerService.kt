@file:OptIn(KordVoice::class)

package es.wokis.services.lavaplayer

import com.github.topi314.lavasrc.ExtendedAudioPlaylist
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason
import dev.kord.common.Locale
import dev.kord.common.annotation.KordVoice
import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.channel.BaseVoiceChannelBehavior
import dev.kord.core.behavior.channel.connect
import dev.kord.core.behavior.edit
import dev.kord.core.entity.Message
import dev.kord.core.entity.channel.MessageChannel
import dev.kord.gateway.retry.LinearRetry
import dev.kord.voice.AudioFrame
import dev.kord.voice.VoiceConnection
import es.wokis.commands.player.createPlayerEmbed
import es.wokis.dispatchers.AppDispatchers
import es.wokis.localization.LocalizationKeys
import es.wokis.services.lavaplayer.model.TrackBO
import es.wokis.services.localization.LocalizationService
import es.wokis.utils.Log
import es.wokis.utils.createCoroutineScope
import es.wokis.utils.getDisplayTrackName
import es.wokis.utils.getLocale
import es.wokis.utils.isValidUrl
import es.wokis.utils.toSanitizedMarkdownLink
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.concurrent.schedule
import kotlin.time.Duration

private const val TAG = "GuildLavaPlayerService"

private const val LEAVE_DELAY = 10000L
private const val FIRST_BACK_OFF_DELAY = "250ms"
private const val MAX_BACK_OFF_DELAY = "2s"
private const val MAX_BACK_OFF_RETRIES = 5
private const val UNKNOWN_ERROR = "Unknown error"
private const val RECONNECT_DELAY = 500L
private const val FRAME_TIMEOUT_MS = 20L

// TODO: Consider splitting into smaller classes (issue: #detekt-suppress)
@Suppress("TooManyFunctions", "ForbiddenComment")
class GuildLavaPlayerService(
    appDispatchers: AppDispatchers,
    private val textChannel: MessageChannel,
    private val voiceChannel: BaseVoiceChannelBehavior,
    private val audioPlayerManager: AudioPlayerManager,
    private val localizationService: LocalizationService,
    val guildId: Snowflake,
    var discordLocale: Locale? = null
) : AudioEventAdapter() {

    private var voiceConnection: VoiceConnection? = null
    private val coroutineScope = createCoroutineScope(TAG, appDispatchers)
    private val queue: MutableList<TrackBO> = mutableListOf()
    private var leaveTimer: Timer? = null
    private val player: AudioPlayer = audioPlayerManager.createPlayer().apply {
        addListener(this@GuildLavaPlayerService)
    }
    private val replayTrackRetry = LinearRetry(
        firstBackoff = Duration.parse(FIRST_BACK_OFF_DELAY),
        maxBackoff = Duration.parse(MAX_BACK_OFF_DELAY),
        maxTries = MAX_BACK_OFF_RETRIES
    )
    private var isRetrying = false
    private var connectingToVoiceChannel: Boolean = false
    private var isReconnecting: Boolean = false
    private var playerMessage: Message? = null
    private var seekTimerJob: Job? = null
    private val updateSeekChannel = Channel<Unit>(Channel.CONFLATED)
    private var frameTimeOut = FRAME_TIMEOUT_MS
    private var currentTrack: TrackBO? = null

    init {
        coroutineScope.launch {
            for (_ in updateSeekChannel) {
                updatePlayerEmbed()
            }
        }
    }

    override fun onTrackEnd(player: AudioPlayer, track: AudioTrack?, endReason: AudioTrackEndReason?) {
        Log.info(endReason?.name.orEmpty().plus(" ").plus(track?.state))
        if (endReason in listOf(AudioTrackEndReason.LOAD_FAILED, AudioTrackEndReason.CLEANUP) && replayTrackRetry.hasNext) {
            try {
                coroutineScope.launch {
                    replayTrackRetry.retry()
                    isRetrying = true
                    player.playTrack(track?.makeClone())
                }
            } catch (e: IllegalStateException) {
                Log.error("An error has occurred on onTrackEnd", exception = e)
                tryPlayNextTrack()
            }
            return
        }
        tryPlayNextTrack()
    }

    override fun onTrackStart(player: AudioPlayer?, track: AudioTrack?) {
        if (isRetrying) return
        coroutineScope.launch {
            discordLocale = voiceChannel.getLocale()
            val voiceChannelName = voiceChannel.asChannel().name
            playerMessage?.let {
                updateSeekChannel.send(Unit)
            } ?: sendNowPlayingMessage(discordLocale, voiceChannelName)
        }
    }

    fun loadAndPlay(url: String, addToFront: Boolean = false) {
        audioPlayerManager.loadItem(
            url,
            getAudioLoadResultHandler(url, addToFront)
        )
    }

    fun loadAndPlayMultiple(tracks: List<String>) {
        tracks.forEachIndexed { index, track ->
            audioPlayerManager.loadItemOrdered(
                index,
                track,
                getAudioLoadResultHandler(track)
            )
        }
    }

    fun loadAndPlayMultipleWithCustomName(tracks: List<String>, customName: String) {
        tracks.forEachIndexed { index, track ->
            audioPlayerManager.loadItemOrdered(
                index,
                track,
                getAudioLoadResultHandlerWithCustomName(track, customName)
            )
        }
    }

    suspend fun loadAndPlayTts(message: String) {
        audioPlayerManager.loadItemSync(message)?.let { item -> item as? AudioTrack }?.let {
            TrackBO(audioTrack = it)
        }?.let { tts ->
            discordLocale = voiceChannel.getLocale()
            connectToVoiceChannel()
            textChannel.createMessage(
                localizationService.getStringFormat(
                    key = LocalizationKeys.ADDED_TRACK_TO_QUEUE,
                    guildId = guildId,
                    discordLocale = discordLocale,
                    arguments = arrayOf(tts.audioTrack.info.title)
                )
            )
            queue(
                tracks = listOf(tts)
            )
        }
    }

    fun searchAndPlay(@Suppress("UNUSED_PARAMETER") searchTerm: String) {
    }

    suspend fun playRadio(radioName: String, radioUrl: String, customFavicon: String) {
        audioPlayerManager.loadItemSync(radioUrl)?.let { item -> item as? AudioTrack }?.let {
            TrackBO(
                customName = radioName,
                customFavicon = customFavicon,
                audioTrack = it
            )
        }?.let {
            connectToVoiceChannel()
            discordLocale = voiceChannel.getLocale()
            textChannel.createMessage(
                localizationService.getStringFormat(
                    key = LocalizationKeys.ADDED_TRACK_TO_QUEUE_WITH_LINK,
                    guildId = guildId,
                    discordLocale = discordLocale,
                    arguments = arrayOf(radioName.toSanitizedMarkdownLink(radioUrl))
                )
            )
            queue(listOf(it))
        }
    }

    fun getQueue(): List<TrackBO> = queue.toList()

    fun getCurrentPlayingTrack(): TrackBO? = currentTrack ?: player.playingTrack?.let { TrackBO(audioTrack = it) }

    fun isPaused(): Boolean = player.isPaused

    fun resume() {
        frameTimeOut = FRAME_TIMEOUT_MS
        player.isPaused = false
    }

    fun pause() {
        frameTimeOut = 0L
        player.isPaused = true
    }

    fun skip() {
        player.stopTrack()
    }

    fun moveTrackToNext(searchTerm: String): TrackBO? {
        val normalizedSearchTerm = searchTerm.lowercase()

        // First, try to find an exact match
        val exactMatchIndex = queue.indexOfFirst { trackBO ->
            trackBO.getDisplayTrackName().lowercase() == normalizedSearchTerm
        }

        // If no exact match, try to find a track that contains the search term
        val trackIndex = if (exactMatchIndex != -1) {
            exactMatchIndex
        } else {
            queue.indexOfFirst { trackBO ->
                trackBO.getDisplayTrackName().lowercase().contains(normalizedSearchTerm)
            }
        }

        return if (trackIndex != -1) {
            val track = queue.removeAt(trackIndex)
            queue.add(0, track)
            updateSeekChannel.trySend(Unit)
            track
        } else {
            null
        }
    }

    fun isQueueEmpty(): Boolean = queue.isEmpty()

    suspend fun stop() {
        handleDisconnectEvent()
    }

    fun shuffle() {
        queue.shuffle()
    }

    suspend fun handleDisconnectEvent() {
        if (isReconnecting) return
        isRetrying = false
        queue.clear()
        player.stopTrack()
        resetVoiceConnection()
        replayTrackRetry.reset()
        resetLeaveTimer()
        resetSeekTimerJob()
        connectingToVoiceChannel = false
        updatePlayerEmbed()
    }

    fun savePlayerMessage(message: Message) {
        this.playerMessage = message
    }

    private fun tryPlayNextTrack() {
        currentTrack = null
        if (queue.isEmpty()) {
            setUpTimer()
        }
        if (queue.isNotEmpty()) {
            playNextTrack()
        }
    }

    private fun playNextTrack() {
        resetLeaveTimer()
        replayTrackRetry.reset()
        isRetrying = false
        nextTrack()
    }

    private fun setUpTimer() {
        leaveTimer = Timer().apply {
            schedule(LEAVE_DELAY) {
                if (queue.isNotEmpty()) return@schedule
                coroutineScope.launch {
                    resetVoiceConnection()
                }
            }
        }
    }

    private fun resetLeaveTimer() {
        leaveTimer?.cancel()
        leaveTimer = null
    }

    private suspend fun sendNowPlayingMessage(
        discordLocale: Locale?,
        voiceChannelName: String
    ) {
        textChannel.createMessage(
            localizationService.getStringFormat(
                key = LocalizationKeys.NOW_PLAYING,
                guildId = guildId,
                discordLocale = discordLocale,
                arguments = arrayOf(currentTrack?.getDisplayTrackName().orEmpty(), voiceChannelName)
            )
        )
    }

    private fun queue(tracks: List<TrackBO>, addToFront: Boolean = false) {
        resetLeaveTimer()
        if (addToFront) {
            queue.addAll(0, tracks)
        } else {
            queue.addAll(tracks)
        }
        if (player.playingTrack == null) {
            nextTrack()
        }
        updateSeekChannel.trySend(Unit)
    }

    private fun nextTrack() {
        val currentTrack = queue.removeAt(0).also {
            currentTrack = it
        }
        player.startTrack(
            currentTrack.audioTrack,
            true
        )
    }

    private fun getAudioLoadResultHandler(currentLoadTrack: String, addToFront: Boolean = false) = object : AudioLoadResultHandler {
        override fun trackLoaded(track: AudioTrack) {
            onTrackLoaded(track, addToFront)
        }

        override fun playlistLoaded(playlist: AudioPlaylist) {
            onPlaylistLoaded(playlist, addToFront)
        }

        override fun noMatches() {
            coroutineScope.launch {
                discordLocale = voiceChannel.getLocale()
                textChannel.createMessage(
                    localizationService.getStringFormat(
                        key = LocalizationKeys.NO_MATCHES,
                        guildId = guildId,
                        discordLocale = discordLocale,
                        arguments = arrayOf(currentLoadTrack)
                    )
                )
            }
        }

        override fun loadFailed(exception: FriendlyException) {
            coroutineScope.launch {
                onLoadFailed(exception)
            }
        }
    }

    private fun getAudioLoadResultHandlerWithCustomName(currentLoadTrack: String, customName: String) = object : AudioLoadResultHandler {
        override fun trackLoaded(track: AudioTrack) {
            onTrackLoadedWithCustomName(track, customName)
        }

        override fun playlistLoaded(playlist: AudioPlaylist) {
            onPlaylistLoaded(playlist)
        }

        override fun noMatches() {
            coroutineScope.launch {
                discordLocale = voiceChannel.getLocale()
                textChannel.createMessage(
                    localizationService.getStringFormat(
                        key = LocalizationKeys.NO_MATCHES,
                        guildId = guildId,
                        discordLocale = discordLocale,
                        arguments = arrayOf(currentLoadTrack)
                    )
                )
            }
        }

        override fun loadFailed(exception: FriendlyException) {
            coroutineScope.launch {
                onLoadFailed(exception)
            }
        }
    }

    private fun onTrackLoadedWithCustomName(track: AudioTrack, customName: String) {
        coroutineScope.launch {
            discordLocale = voiceChannel.getLocale()
            val currentTrack = TrackBO(
                customName = customName,
                audioTrack = track
            )
            textChannel.createMessage(
                localizationService.getStringFormat(
                    key = LocalizationKeys.ADDED_TRACK_TO_QUEUE,
                    guildId = guildId,
                    discordLocale = discordLocale,
                    arguments = arrayOf(currentTrack.getDisplayTrackName())
                )
            )
            connectToVoiceChannel()
            queue(listOf(currentTrack))
        }
    }

    private fun onPlaylistLoaded(playlist: AudioPlaylist, addToFront: Boolean = false) {
        coroutineScope.launch {
            discordLocale = voiceChannel.getLocale()
            val isCurrentlyPlaying = player.playingTrack != null
            val message = textChannel.createMessage(
                localizationService.getStringFormat(
                    key = LocalizationKeys.FOUND_TRACK_LIST,
                    guildId = guildId,
                    discordLocale = discordLocale,
                    arguments = arrayOf(playlist.name, playlist.tracks.size)
                )
            )
            connectToVoiceChannel()
            queue(playlist.tracks.map { TrackBO(audioTrack = it) }, addToFront)
            if (addToFront && !isCurrentlyPlaying) {
                nextTrack()
            }
            val playlistUrl = (playlist as? ExtendedAudioPlaylist)?.url
            message.edit {
                content = if (playlistUrl?.isValidUrl() == true) {
                    localizationService.getStringFormat(
                        key = if (addToFront) {
                            LocalizationKeys.NEXT_ADDED_SONGS_TO_QUEUE_WITH_LINK
                        } else {
                            LocalizationKeys.ADDED_SONGS_TO_QUEUE_WITH_LINK
                        },
                        guildId = guildId,
                        discordLocale = discordLocale,
                        arguments = arrayOf(
                            playlist.name.toSanitizedMarkdownLink(playlistUrl),
                            playlist.tracks.size
                        )
                    )
                } else {
                    localizationService.getStringFormat(
                        key = if (addToFront) LocalizationKeys.NEXT_ADDED_SONGS_TO_QUEUE else LocalizationKeys.ADDED_SONGS_TO_QUEUE,
                        guildId = guildId,
                        discordLocale = discordLocale,
                        arguments = arrayOf(playlist.name, playlist.tracks.size)
                    )
                }
            }
        }
    }

    private fun onTrackLoaded(track: AudioTrack, addToFront: Boolean = false) {
        coroutineScope.launch {
            discordLocale = voiceChannel.getLocale()
            val currentTrack = TrackBO(audioTrack = track)
            val isCurrentlyPlaying = player.playingTrack != null
            textChannel.createMessage(
                if (track.info.uri.isValidUrl()) {
                    localizationService.getStringFormat(
                        key = if (addToFront) {
                            LocalizationKeys.NEXT_ADDED_TO_QUEUE_WITH_LINK
                        } else {
                            LocalizationKeys.ADDED_TRACK_TO_QUEUE_WITH_LINK
                        },
                        guildId = guildId,
                        discordLocale = discordLocale,
                        arguments = arrayOf(
                            currentTrack.getDisplayTrackName().toSanitizedMarkdownLink(track.info.uri)
                        )
                    )
                } else {
                    localizationService.getStringFormat(
                        key = if (addToFront) {
                            LocalizationKeys.NEXT_ADDED_TO_QUEUE
                        } else {
                            LocalizationKeys.ADDED_TRACK_TO_QUEUE
                        },
                        guildId = guildId,
                        discordLocale = discordLocale,
                        arguments = arrayOf(currentTrack.getDisplayTrackName())
                    )
                }
            )
            connectToVoiceChannel()
            queue(listOf(currentTrack), addToFront)
            if (addToFront && !isCurrentlyPlaying) {
                nextTrack()
            }
        }
    }

    fun isConnected(): Boolean = voiceConnection != null

    suspend fun reconnect() {
        if (isReconnecting || !isConnected()) return
        isReconnecting = true
        resetVoiceConnection()
        delay(RECONNECT_DELAY)
        connectToVoiceChannel()
        isReconnecting = false
    }

    private suspend fun connectToVoiceChannel() {
        if (voiceConnection == null && connectingToVoiceChannel.not()) {
            connectingToVoiceChannel = true
            voiceConnection = voiceChannel.connect {
                audioProvider { AudioFrame.fromData(player.provide(frameTimeOut, TimeUnit.MILLISECONDS)?.data) }
                selfDeaf = true
            }.also {
                connectingToVoiceChannel = false
            }
        }
    }

    private suspend fun onLoadFailed(exception: FriendlyException) {
        Log.error("GuildLavaPlayerService onLoadFailed", exception)
        discordLocale = voiceChannel.getLocale()
        textChannel.createMessage(
            localizationService.getStringFormat(
                key = LocalizationKeys.LOAD_FAILED,
                guildId = guildId,
                discordLocale = discordLocale,
                arguments = arrayOf(exception.message ?: UNKNOWN_ERROR)
            )
        )
    }

    private fun resetSeekTimerJob() {
        seekTimerJob?.cancel()
        seekTimerJob = null
    }

    private suspend fun resetVoiceConnection() {
        voiceConnection?.leave()
        voiceConnection = null
    }

    private suspend fun updatePlayerEmbed() {
        playerMessage?.let {
            val guildName = textChannel.data.guildId.value?.let { guildId ->
                textChannel.kord.getGuild(
                    guildId
                )
            }?.name.orEmpty()
            try {
                it.edit {
                    createPlayerEmbed(
                        guildId = guildId,
                        discordLocale = discordLocale,
                        guildName = guildName,
                        localizationService = localizationService,
                        currentTrack = currentTrack,
                        queue = queue,
                        isPaused = player.isPaused
                    )
                }
            } catch (t: Throwable) {
                Log.error("There's been an error trying to update the embed message on $guildName", t)
            }
        }
    }
}
