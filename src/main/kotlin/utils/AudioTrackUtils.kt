package es.wokis.utils

import com.sedmelluq.discord.lavaplayer.track.AudioTrack

fun AudioTrack.getDisplayTrackName(): String {
    if (isFromFloweryTTS()) {
        return "FloweryTTS - tts message"
    }
    return if (info.title.contains(" - ")) info.title else "${info.author} - ${info.title}"
}

fun AudioTrack.isFromFloweryTTS() = info.author.lowercase().contains("flowery-tts")
