package es.wokis.utils

import es.wokis.services.lavaplayer.model.TrackBO

/** Kord-side alias for the neutral [es.wokis.player.engine.model.PlayerTrack.displayName]. */
fun TrackBO.getDisplayTrackName(): String = displayName
