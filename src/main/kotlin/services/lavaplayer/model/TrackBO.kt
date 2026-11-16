package es.wokis.services.lavaplayer.model

/**
 * Kord-side compatibility alias for the shared [es.wokis.player.engine.model.PlayerTrack];
 * commands keep importing `TrackBO` so presentation code does not depend on the engine directly.
 */
typealias TrackBO = es.wokis.player.engine.model.PlayerTrack
