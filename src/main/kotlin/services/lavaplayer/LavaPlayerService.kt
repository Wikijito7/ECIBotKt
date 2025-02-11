package es.wokis.services.lavaplayer

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers

class LavaPlayerService {
    private val player: AudioPlayer

    init {
        val playerManager: AudioPlayerManager = DefaultAudioPlayerManager().apply {
            AudioSourceManagers.registerRemoteSources(this)
        }
        player = playerManager.createPlayer().apply {
            addListener(TrackScheduler(this))
        }
    }

}