package es.wokis.services.lavaplayer

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
import es.wokis.services.localization.LocalizationService
import es.wokis.utils.Log
import es.wokis.utils.createCoroutineScope
import es.wokis.utils.getDisplayTrackName
import es.wokis.utils.getLocale
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.text.SimpleDateFormat
import java.util.*
import kotlin.concurrent.schedule
import kotlin.time.Duration

private const val TAG = "GuildLavaPlayerService"

private const val LEAVE_DELAY = 10000L
private const val FIRST_BACK_OFF_DELAY = "250ms"
private const val MAX_BACK_OFF_DELAY = "2s"
private const val MAX_BACK_OFF_RETRIES = 5
private const val UNKNOWN_ERROR = "Unknown error"
private const val SEEK_UPDATE_DELAY = 3000L

class GuildLavaPlayerService(
    appDispatchers: AppDispatchers,
    private val textChannel: MessageChannel,
    private val voiceChannel: BaseVoiceChannelBehavior,
    private val audioPlayerManager: AudioPlayerManager,
    private val localizationService: LocalizationService
) : AudioEventAdapter() {

    @OptIn(KordVoice::class)
    private var voiceConnection: VoiceConnection? = null
    private val coroutineScope = createCoroutineScope(TAG, appDispatchers)
    private val queue: MutableList<AudioTrack> = mutableListOf()
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
    private var playerMessage: Message? = null
    private var seekTimerJob: Job? = null
    private val updateSeekChannel = Channel<Unit>(Channel.CONFLATED)

    init {
        coroutineScope.launch {
            for (event in updateSeekChannel) {
                updatePlayerEmbed()
            }
        }
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

    suspend fun loadAndPlayTts(messages: List<String>) {
        messages.mapNotNull {
            audioPlayerManager.loadItemSync(it)?.let { item -> item as? AudioTrack }
        }.let { ttsQueue ->
            val locale = voiceChannel.getLocale()
            connectToVoiceChannel()
            queue(ttsQueue)
            textChannel.createMessage(
                localizationService.getStringFormat(
                    key = LocalizationKeys.ADDED_SONGS_TO_QUEUE,
                    locale = locale,
                    arguments = arrayOf(ttsQueue.size)
                )
            )
        }
    }

    fun loadAndPlay(url: String) {
        audioPlayerManager.loadItem(
            url,
            getAudioLoadResultHandler(url)
        )
    }

    fun searchAndPlay(searchTerm: String) {
        // TODO: Implement on next steps
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

    private fun tryPlayNextTrack() {
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

    override fun onTrackStart(player: AudioPlayer?, track: AudioTrack?) {
        if (isRetrying) return
        coroutineScope.launch {
            val locale = voiceChannel.getLocale()
            val voiceChannelName = voiceChannel.asChannel().name
            playerMessage?.let {
                updateSeekChannel.send(Unit)
            } ?: sendNowPlayingMessage(locale, track, voiceChannelName)
        }
    }

    // TODO: Take a look in the future to solve discord update request error or delete it
    private fun startSeekUpdateTimer() {
        if (playerMessage == null) return
        resetSeekTimerJob()
        seekTimerJob = coroutineScope.launch {
            while (true) {
                updateSeekChannel.send(Unit)
                delay(SEEK_UPDATE_DELAY)
            }
        }
    }

    private suspend fun sendNowPlayingMessage(
        locale: Locale,
        track: AudioTrack?,
        voiceChannelName: String
    ) {
        textChannel.createMessage(
            localizationService.getStringFormat(
                key = LocalizationKeys.NOW_PLAYING,
                locale = locale,
                arguments = arrayOf(track?.getDisplayTrackName().orEmpty(), voiceChannelName)
            )
        )
    }

    fun getQueue(): List<AudioTrack> = queue.toList()

    fun getCurrentPlayingTrack(): AudioTrack? = player.playingTrack

    fun isPaused(): Boolean = player.isPaused

    fun resume() {
        player.isPaused = false
    }

    fun pause() {
        player.isPaused = true
    }

    fun skip() {
        player.stopTrack()
    }

    suspend fun stop() {
        handleDisconnectEvent()
    }

    fun shuffle() {
        queue.shuffle()
    }

    private fun queue(track: List<AudioTrack>) {
        resetLeaveTimer()
        queue.addAll(track)
        if (player.playingTrack == null) {
            nextTrack()
        }
        updateSeekChannel.trySend(Unit)
    }

    private fun nextTrack() {
        player.startTrack(queue.removeAt(0), true)
    }

    private fun getAudioLoadResultHandler(currentLoadTrack: String) = object : AudioLoadResultHandler {
        override fun trackLoaded(track: AudioTrack) {
            onTrackLoaded(track)
        }

        override fun playlistLoaded(playlist: AudioPlaylist) {
            onPlaylistLoaded(playlist)
        }

        override fun noMatches() {
            coroutineScope.launch {
                val locale = voiceChannel.getLocale()
                textChannel.createMessage(
                    localizationService.getStringFormat(
                        key = LocalizationKeys.NO_MATCHES,
                        locale = locale,
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

    private fun onPlaylistLoaded(playlist: AudioPlaylist) {
        coroutineScope.launch {
            val locale = voiceChannel.getLocale()
            val message = textChannel.createMessage(
                localizationService.getStringFormat(
                    key = LocalizationKeys.FOUND_TRACK_LIST,
                    locale = locale,
                    arguments = arrayOf(playlist.name, playlist.tracks.size)
                )
            )
            connectToVoiceChannel()
            queue(playlist.tracks)
            message.edit {
                content = localizationService.getStringFormat(
                    key = LocalizationKeys.ADDED_SONGS_TO_QUEUE,
                    locale = locale,
                    arguments = arrayOf(playlist.tracks.size)
                )
            }
        }
    }

    private fun onTrackLoaded(track: AudioTrack) {
        coroutineScope.launch {
            val locale = voiceChannel.getLocale()
            textChannel.createMessage(
                localizationService.getStringFormat(
                    key = LocalizationKeys.ADDED_TRACK_TO_QUEUE,
                    locale = locale,
                    arguments = arrayOf(track.getDisplayTrackName())
                )
            )
            connectToVoiceChannel()
            queue(listOf(track))
        }
    }

    @OptIn(KordVoice::class)
    private suspend fun connectToVoiceChannel() {
        if (voiceConnection == null && connectingToVoiceChannel.not()) {
            connectingToVoiceChannel = true
            voiceConnection = voiceChannel.connect {
                audioProvider { AudioFrame.fromData(player.provide()?.data) }
                selfDeaf = true
            }
        }
    }

    private suspend fun onLoadFailed(exception: FriendlyException) {
        Log.error("GuildLavaPlayerService onLoadFailed", exception)
        val locale = voiceChannel.getLocale()
        textChannel.createMessage(
            localizationService.getStringFormat(
                key = LocalizationKeys.LOAD_FAILED,
                locale = locale,
                arguments = arrayOf(exception.message ?: UNKNOWN_ERROR)
            )
        )
    }

    suspend fun handleDisconnectEvent() {
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

    private fun resetSeekTimerJob() {
        seekTimerJob?.cancel()
        seekTimerJob = null
    }

    @OptIn(KordVoice::class)
    private suspend fun resetVoiceConnection() {
        voiceConnection?.leave()
        voiceConnection = null
    }

    fun savePlayerMessage(message: Message) {
        this.playerMessage = message
        updateSeekChannel.trySend(Unit)
    }

    private suspend fun updatePlayerEmbed() {
        playerMessage?.let {
            it.edit {
                createPlayerEmbed(player.playingTrack, queue, player.isPaused)
            }
        }
    }
}
