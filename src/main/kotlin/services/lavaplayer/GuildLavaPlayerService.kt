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
import dev.kord.core.entity.Message
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
    private var leaveTimer: Timer? = null

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
            resetTimer()
            nextTrack()
        }
        if (queue.isEmpty()) {
            setUpTimer()
        }
    }

    @OptIn(KordVoice::class)
    private fun setUpTimer() {
        leaveTimer = Timer().apply {
            Log.info("Scheduling leave delay")
            schedule(LEAVE_DELAY) {
                Log.info("Executing leave procedure")
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
            textChannel.createMessage("Now playing: ${track?.info?.author} ${track?.info?.title}")
        }
    }

    override fun getQueue(): List<AudioTrack> = queue.toList()

    override fun getCurrentPlayingTrack(): AudioTrack? = player.playingTrack

    private fun queue(track: AudioTrack) {
        queue.add(track)
        if (player.playingTrack == null) {
            nextTrack()
        }
    }

    private fun nextTrack() {
        Log.info("Playing next track")
        player.startTrack(queue.removeAt(0), true)
    }


    private fun audioLoadResultHandler() = object : AudioLoadResultHandler {
        override fun trackLoaded(track: AudioTrack) {
            coroutineScope.launch {
                onTrackLoaded(track)
            }
            queue(track)
        }

        override fun playlistLoaded(playlist: AudioPlaylist) {
        coroutineScope.launch {
            val message = textChannel.createMessage("Found track list ${playlist.name} with ${playlist.tracks.size} tracks")
                playlist.tracks.forEach { track ->
                    onTrackLoaded(track, message)
                    queue(track)
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
    private suspend fun onTrackLoaded(track: AudioTrack, message: Message? = null) {
        if (voiceConnection == null) {
            voiceConnection = voiceChannel.connect {
                audioProvider { AudioFrame.fromData(player.provide()?.data) }
                selfDeaf = true
            }
        }
        val messageContent = "Added ${track.info.author} - ${track.info.title} to the queue."
        message?.edit {
            content = messageContent
        } ?: textChannel.createMessage(messageContent)
    }

    private suspend fun onNoMatches() {
        textChannel.createMessage("No matches found")
    }

    private suspend fun onLoadFailed(exception: FriendlyException) {
        Log.error("GuildLavaPlayerService onLoadFailed", exception)
        textChannel.createMessage("Load failed: ${exception.message}")
    }
}
