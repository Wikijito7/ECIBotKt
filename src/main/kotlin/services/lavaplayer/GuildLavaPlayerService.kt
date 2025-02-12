package es.wokis.services.lavaplayer

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import dev.kord.common.annotation.KordVoice
import dev.kord.core.behavior.channel.BaseVoiceChannelBehavior
import dev.kord.core.behavior.channel.connect
import dev.kord.core.entity.channel.MessageChannel
import dev.kord.voice.AudioFrame
import dev.lavalink.youtube.YoutubeAudioSourceManager
import es.wokis.dispatchers.AppDispatchers
import es.wokis.utils.createCoroutineScope
import kotlinx.coroutines.launch

private const val TAG = "GuildLavaPlayerService"

class GuildLavaPlayerService(
    appDispatchers: AppDispatchers,
    youtubeOauth2Token: String,
    private val textChannel: MessageChannel,
    private val voiceChannel: BaseVoiceChannelBehavior
) {
    private val player: AudioPlayer
    private val trackScheduler: TrackScheduler
    private val audioPlayerManager: AudioPlayerManager = DefaultAudioPlayerManager().apply {
        AudioSourceManagers.registerLocalSource(this)
        AudioSourceManagers.registerRemoteSources(
            this,
            com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeAudioSourceManager::class.java
        )
        val ytSourceManager = YoutubeAudioSourceManager().apply {
            useOauth2(youtubeOauth2Token, true)
        }
        this.registerSourceManager(ytSourceManager)

        player = createPlayer().apply {
            trackScheduler = TrackScheduler(this)
            addListener(trackScheduler)
        }
    }
    private val coroutineScope = createCoroutineScope(TAG, appDispatchers)

    fun loadAndPlay(searchInput: String) {
        audioPlayerManager.loadItem(
            searchInput,
            audioLoadResultHandler()
        )
    }

    private fun audioLoadResultHandler() = object : AudioLoadResultHandler {
        override fun trackLoaded(track: AudioTrack) {
            trackScheduler.queue(track)
            coroutineScope.launch {
                onTrackLoaded(track)
            }
        }

        override fun playlistLoaded(playlist: AudioPlaylist) {
            playlist.tracks.forEach { track ->
                trackScheduler.queue(track)
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
        voiceChannel.connect {
            audioProvider { AudioFrame.fromData(player.provide()?.data) }
        }
        textChannel.createMessage("Now playing: ${track.info.author} ${track.info.title}")
    }

    private suspend fun onNoMatches() {
        textChannel.createMessage("No matches found")
    }

    private suspend fun onLoadFailed(exception: FriendlyException) {
        textChannel.createMessage("Load failed: ${exception.message}")
    }
}
