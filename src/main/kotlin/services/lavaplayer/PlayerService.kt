package es.wokis.services.lavaplayer

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager
import com.sedmelluq.discord.lavaplayer.track.AudioTrack

interface PlayerService {
    val player: AudioPlayer
    val audioPlayerManager: AudioPlayerManager

    fun loadAndPlay(url: String)

    fun searchAndPlay(searchTerm: String)

    fun getQueue(): List<AudioTrack>

    fun getCurrentPlayingTrack(): AudioTrack?
}