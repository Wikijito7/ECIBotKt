package es.wokis.services.lavaplayer.model

/**
 * Kord-side compatibility name for the neutral player track snapshot.
 *
 * The queue/player state now belongs to the shared `:player` engine; commands keep using
 * `TrackBO` so their presentation code does not depend on the engine module directly.
 */
typealias TrackBO = es.wokis.player.engine.model.PlayerTrack
