package es.wokis.utils

import es.wokis.services.lavaplayer.model.TrackBO

fun TrackBO.getDisplayTrackName(): String = when {
    customName != null -> customName
    audioTrack.info.title.contains(" - ") -> audioTrack.info.title
    else -> "${audioTrack.info.author} - ${audioTrack.info.title}"
}
