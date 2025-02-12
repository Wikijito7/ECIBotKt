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
import dev.kord.core.entity.channel.MessageChannel
import dev.kord.voice.AudioFrame
import dev.kord.voice.VoiceConnection
import dev.lavalink.youtube.YoutubeAudioSourceManager
import es.wokis.bot.Bot
import es.wokis.dispatchers.AppDispatchers
import es.wokis.utils.createCoroutineScope
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

private const val TAG = "GuildLavaPlayerService"

class GuildLavaPlayerService(
    appDispatchers: AppDispatchers,
    youtubeOauth2Token: String,
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

    override fun loadAndPlay(url: String) {
        audioPlayerManager.loadItem(
            url,
            audioLoadResultHandler()
        )
    }

    override fun searchAndPlay(searchTerm: String) {
        // TODO: Implement on next steps
    }

    override fun onTrackEnd(player: AudioPlayer, track: AudioTrack?, endReason: AudioTrackEndReason?) {
        if (endReason?.mayStartNext == true && queue.isNotEmpty()) {
            nextTrack()
        }
    }

    override fun onTrackStart(player: AudioPlayer?, track: AudioTrack?) {
        coroutineScope.launch {
            textChannel.createMessage("Now playing: ${track?.info?.author} ${track?.info?.title}")
        }
    }

    override fun getQueue(): List<AudioTrack> = queue.toList()

    override fun getCurrentPlayingTrack(): AudioTrack {
        TODO("Not yet implemented")
    }

    private fun queue(track: AudioTrack) {
        queue.add(track)
        if (player.playingTrack == null) {
            nextTrack()
        }
    }

    private fun nextTrack() {
        LoggerFactory.getLogger(Bot::class.java).info("Playing next track")
        player.startTrack(queue.removeAt(0), true)
    }


    private fun audioLoadResultHandler() = object : AudioLoadResultHandler {
        override fun trackLoaded(track: AudioTrack) {
            queue(track)
            coroutineScope.launch {
                onTrackLoaded(track)
            }
        }

        override fun playlistLoaded(playlist: AudioPlaylist) {
            playlist.tracks.forEach { track ->
                queue(track)
                coroutineScope.launch {
                    onTrackLoaded(track)
                }
            }
        }

        override fun noMatches() {
            coroutineScope.launch {
                onNoMatches()
            }
        }

        override fun loadFailed(exception: FriendlyException) {
            exception.printStackTrace()
            coroutineScope.launch {
                onLoadFailed(exception)
            }
        }
    }

    @OptIn(KordVoice::class)
    private suspend fun onTrackLoaded(track: AudioTrack) {
        if (voiceConnection == null) {
            voiceConnection = voiceChannel.connect {
                audioProvider { AudioFrame.fromData(player.provide()?.data) }
            }
        }
    }

    private suspend fun onNoMatches() {
        textChannel.createMessage("No matches found")
    }

    private suspend fun onLoadFailed(exception: FriendlyException) {
        textChannel.createMessage("Load failed: ${exception.message}")
    }
}
