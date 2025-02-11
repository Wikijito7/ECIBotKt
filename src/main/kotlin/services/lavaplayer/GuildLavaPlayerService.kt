package es.wokis.services.lavaplayer

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import dev.kord.common.entity.Snowflake
import es.wokis.dispatchers.AppDispatchers
import es.wokis.utils.createCoroutineScope
import kotlinx.coroutines.launch

private const val TAG = "GuildLavaPlayerService"

class GuildLavaPlayerService(
    appDispatchers: AppDispatchers,
    private val textChannelId: Snowflake,
    private val onNoMatches: suspend (Snowflake) -> Unit,
    private val onLoadFailed: suspend (Snowflake, FriendlyException) -> Unit,
    private val onTrackLoaded: suspend (Snowflake, Snowflake, String) -> Unit,
    private val voiceChannelId: Snowflake,
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

    fun loadAndPlay() {
        audioPlayerManager.loadItem(
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            object : AudioLoadResultHandler {
                override fun trackLoaded(track: AudioTrack) {
                    trackScheduler.queue(track)
                    coroutineScope.launch {
                        onTrackLoaded(textChannelId, voiceChannelId, track.info.title)
                    }
                }

                override fun playlistLoaded(playlist: AudioPlaylist) {
                    playlist.tracks.forEach { track ->
                        trackScheduler.queue(track)
                        coroutineScope.launch {
                            onTrackLoaded(textChannelId, voiceChannelId, track.info.title)
                        }
                    }
                }

                override fun noMatches() {
                    coroutineScope.launch {
                        onNoMatches(textChannelId)
                    }
                }

                override fun loadFailed(exception: FriendlyException) {
                    coroutineScope.launch {
                        onLoadFailed(textChannelId, exception)
                    }
                }
            }
        )
    }
}
