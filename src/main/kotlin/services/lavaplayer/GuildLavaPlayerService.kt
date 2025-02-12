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
import es.wokis.dispatchers.AppDispatchers
import es.wokis.utils.createCoroutineScope
import kotlinx.coroutines.launch

private const val TAG = "GuildLavaPlayerService"

class GuildLavaPlayerService(
    appDispatchers: AppDispatchers,
    private val textChannel: MessageChannel,
    private val voiceChannel: BaseVoiceChannelBehavior,
) {
    private val player: AudioPlayer
    private val trackScheduler: TrackScheduler
    private val audioPlayerManager: AudioPlayerManager = DefaultAudioPlayerManager().apply {
        AudioSourceManagers.registerRemoteSources(this)
        AudioSourceManagers.registerLocalSource(this)

        player = createPlayer().apply {
            trackScheduler = TrackScheduler(this)
            addListener(trackScheduler)
        }
    }
    private val coroutineScope = createCoroutineScope(TAG, appDispatchers)

    fun loadAndPlay(searchInput: String) {
        audioPlayerManager.loadItem(
            searchInput,
            object : AudioLoadResultHandler {
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
                    coroutineScope.launch {
                        onLoadFailed(exception)
                    }
                }
            }
        )
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
