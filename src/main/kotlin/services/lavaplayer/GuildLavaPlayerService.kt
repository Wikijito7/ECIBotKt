package es.wokis.services.lavaplayer

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers
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
import dev.lavalink.youtube.YoutubeAudioSourceManager
import es.wokis.dispatchers.AppDispatchers
import es.wokis.utils.Log
import es.wokis.utils.createCoroutineScope
import kotlinx.coroutines.launch
import java.util.Timer
import kotlin.concurrent.schedule

private const val TAG = "GuildLavaPlayerService"

private const val LEAVE_DELAY = 10000L

class GuildLavaPlayerService(
    appDispatchers: AppDispatchers,
    youtubeOauth2Token: String?,
    private val textChannel: MessageChannel,
    private val voiceChannel: BaseVoiceChannelBehavior
) : AudioEventAdapter(), PlayerService {

    override val player: AudioPlayer
    override val audioPlayerManager: AudioPlayerManager = DefaultAudioPlayerManager().apply {
        val ytSourceManager = YoutubeAudioSourceManager().apply {
            useOauth2(youtubeOauth2Token, true)
        }
        this.registerSourceManager(ytSourceManager)
        AudioSourceManagers.registerLocalSource(this)
        AudioSourceManagers.registerRemoteSources(
            this,
            com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeAudioSourceManager::class.java
        )

        player = createPlayer().apply {
            addListener(this@GuildLavaPlayerService)
        }
    }

    @OptIn(KordVoice::class)
    private var voiceConnection: VoiceConnection? = null
    private val coroutineScope = createCoroutineScope(TAG, appDispatchers)
    private val queue: MutableList<AudioTrack> = mutableListOf()
    private var leaveTimer: Timer? = null

    override fun loadAndPlay(url: String) {
        audioPlayerManager.loadItem(
            url,
            getAudioLoadResultHandler()
        )
    }

    override fun searchAndPlay(searchTerm: String) {
        // TODO: Implement on next steps
    }

    override fun onTrackEnd(player: AudioPlayer, track: AudioTrack?, endReason: AudioTrackEndReason?) {
        if (queue.isEmpty()) {
            setUpTimer()
        }
        if (endReason?.mayStartNext == true && queue.isNotEmpty()) {
            resetTimer()
            nextTrack()
        }
    }

    @OptIn(KordVoice::class)
    private fun setUpTimer() {
        leaveTimer = Timer().apply {
            schedule(LEAVE_DELAY) {
                if (queue.isNotEmpty()) return@schedule
                coroutineScope.launch {
                    voiceConnection?.leave()
                    voiceConnection = null
                }
            }
        }
    }

    private fun resetTimer() {
        leaveTimer?.cancel()
        leaveTimer = null
    }

    override fun onTrackStart(player: AudioPlayer?, track: AudioTrack?) {
        coroutineScope.launch {
            textChannel.createMessage("Now playing: ${track?.info?.author} - ${track?.info?.title}")
        }
    }

    override fun getQueue(): List<AudioTrack> = queue.toList()

    override fun getCurrentPlayingTrack(): AudioTrack? = player.playingTrack

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
                textChannel.createMessage("No matches found")
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
            val message = textChannel.createMessage("Found track list ${playlist.name} with ${playlist.tracks.size} tracks.")
            connectToVoiceChannel()
            queue(playlist.tracks)
            message.edit { content = "Added ${playlist.tracks.size} songs to the queue." }
        }
    }

    private fun onTrackLoaded(track: AudioTrack) {
        coroutineScope.launch {
            textChannel.createMessage("Added ${track.info.author} - ${track.info.title} to the queue.")
            connectToVoiceChannel()
            queue(listOf(track))
        }
    }

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
        textChannel.createMessage("Load failed: ${exception.message}")
    }
}
