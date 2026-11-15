package es.wokis.utils

import es.wokis.services.lavaplayer.model.TrackBO

/**
 * Display name used by the existing queue/player embeds.
 *
 * Kept as a Kord-side extension so command code keeps its previous import while the underlying
 * value comes from the neutral [TrackBO] snapshot (`PlayerTrack.displayName`).
 */
fun TrackBO.getDisplayTrackName(): String = displayName
