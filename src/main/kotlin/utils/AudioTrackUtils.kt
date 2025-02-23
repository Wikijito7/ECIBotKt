package es.wokis.utils

import com.sedmelluq.discord.lavaplayer.track.AudioTrack

fun AudioTrack.getDisplayTrackName(): String =
    if (info.title.contains(" - ")) info.title else "${info.author} - ${info.title}"
