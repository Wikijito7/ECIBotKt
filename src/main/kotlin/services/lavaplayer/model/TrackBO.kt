package es.wokis.services.lavaplayer.model

import com.sedmelluq.discord.lavaplayer.track.AudioTrack

data class TrackBO(
    val customName: String? = null,
    val customFavicon: String? = null,
    val audioTrack: AudioTrack
)