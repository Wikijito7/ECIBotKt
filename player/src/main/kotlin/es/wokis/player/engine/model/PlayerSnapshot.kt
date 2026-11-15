package es.wokis.player.engine.model

/**
 * Immutable view of one guild's player state.
 *
 * [current] is the track Lavaplayer is playing (or paused on). [queue] is the pending queue,
 * excluding [current]. Durations and positions are milliseconds.
 */
data class PlayerSnapshot(
    val current: PlayerTrack? = null,
    val queue: List<PlayerTrack> = emptyList(),
    val paused: Boolean = false,
    /** True when a track owns the player; this is not a gateway voice-connection state. */
    val hasActiveTrack: Boolean = false,
)
