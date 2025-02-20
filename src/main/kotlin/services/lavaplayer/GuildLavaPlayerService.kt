package es.wokis.services.lavaplayer

import co.touchlab.stately.concurrency.AtomicInt
import co.touchlab.stately.concurrency.value
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason
import dev.kord.common.annotation.KordVoice
import dev.kord.core.behavior.channel.BaseVoiceChannelBehavior
import dev.kord.core.behavior.channel.connect
import dev.kord.core.behavior.edit
import dev.kord.core.entity.channel.MessageChannel
import dev.kord.voice.AudioFrame
import dev.kord.voice.VoiceConnection
import es.wokis.dispatchers.AppDispatchers
import es.wokis.localization.LocalizationKeys
import es.wokis.services.localization.LocalizationService
import es.wokis.utils.Log
import es.wokis.utils.createCoroutineScope
import es.wokis.utils.getLocale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Timer
import kotlin.concurrent.schedule

private const val TAG = "GuildLavaPlayerService"

private const val LEAVE_DELAY = 10000L
private val BACK_OFF_DELAY = 1000L

private const val UNKNOWN_ERROR = "Unknown error"

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
    private var playTrackRetries: AtomicInt = AtomicInt(0)
    private val player: AudioPlayer = audioPlayerManager.createPlayer().apply {
        addListener(this@GuildLavaPlayerService)
    }

    fun loadAndPlay(url: String) {
        audioPlayerManager.loadItem(
            url,
            getAudioLoadResultHandler()
        )
    }

    fun searchAndPlay(searchTerm: String) {
        // TODO: Implement on next steps
    }

    override fun onTrackEnd(player: AudioPlayer, track: AudioTrack?, endReason: AudioTrackEndReason?) {
        Log.info(endReason?.name.orEmpty())
        if (endReason in listOf(AudioTrackEndReason.LOAD_FAILED, AudioTrackEndReason.CLEANUP) && playTrackRetries.value <= 3) {
            coroutineScope.launch {
                delay(playTrackRetries.addAndGet(1) * BACK_OFF_DELAY)
                player.playTrack(track?.makeClone())
            }
            return
        }
        if (queue.isEmpty()) {
            setUpTimer()
        }
        if (endReason?.mayStartNext == true && queue.isNotEmpty()) {
            resetTimer()
            nextTrack()
            playTrackRetries.set(0)
        }
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

    private fun resetTimer() {
        leaveTimer?.cancel()
        leaveTimer = null
    }

    override fun onTrackStart(player: AudioPlayer?, track: AudioTrack?) {
        if (playTrackRetries.value > 0) return
        coroutineScope.launch {
            val locale = voiceChannel.getLocale()
            val voiceChannelName = voiceChannel.asChannel().name
            textChannel.createMessage(
                localizationService.getStringFormat(
                    key = LocalizationKeys.NOW_PLAYING,
                    locale = locale,
                    arguments = arrayOf(track?.getDisplayTrackName().orEmpty(), voiceChannelName)
                )
            )
        }
    }

    fun getQueue(): List<AudioTrack> = queue.toList()

    fun getCurrentPlayingTrack(): AudioTrack? = player.playingTrack

    private fun queue(track: List<AudioTrack>) {
        resetTimer()
        queue.addAll(track)
        if (player.playingTrack == null) {
            nextTrack()
        }
    }

    private fun nextTrack() {
        player.startTrack(queue.removeAt(0), true)
    }

    private fun getAudioLoadResultHandler() = object : AudioLoadResultHandler {
        override fun trackLoaded(track: AudioTrack) {
            onTrackLoaded(track)
        }

        override fun playlistLoaded(playlist: AudioPlaylist) {
            onPlaylistLoaded(playlist)
        }

        override fun noMatches() {
            coroutineScope.launch {
                val locale = voiceChannel.getLocale()
                textChannel.createMessage(localizationService.getString(LocalizationKeys.NO_MATCHES, locale))
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

    private fun AudioTrack.getDisplayTrackName(): String =
        if (info.title.contains(" - ")) info.title else "${info.author} - ${info.title}"

    @OptIn(KordVoice::class)
    private suspend fun connectToVoiceChannel() {
        if (voiceConnection == null) {
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
        queue.clear()
        player.stopTrack()
        resetVoiceConnection()
        playTrackRetries.set(0)
    }

    @OptIn(KordVoice::class)
    private suspend fun resetVoiceConnection() {
        voiceConnection?.leave()
        voiceConnection = null
    }
}
