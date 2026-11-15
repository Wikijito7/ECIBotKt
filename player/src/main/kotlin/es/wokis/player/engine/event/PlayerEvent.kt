package es.wokis.player.engine.event

import es.wokis.player.engine.model.PlayerSnapshot
import es.wokis.player.engine.model.PlayerTrack

/**
 * Events emitted by the serialized player engine after a state transition.
 *
 * Adapters consume these to update their gateway-specific presentation (Kord messages/embeds,
 * JDA diagnostics). Lavaplayer callbacks never emit directly: they enqueue a mailbox command, and
 * the actor emits the event after applying the transition, so event order matches state order.
 */
sealed interface PlayerEvent {

    /** A single track was loaded and added to the queue. */
    data class TrackQueued(
        val track: PlayerTrack,
        val addToFront: Boolean,
        val uri: String?,
    ) : PlayerEvent

    /** A playlist was loaded and its tracks were added to the queue. */
    data class PlaylistQueued(
        val name: String,
        val trackCount: Int,
        val playlistUrl: String?,
        val addToFront: Boolean,
    ) : PlayerEvent

    /** A load found no match for the supplied input. */
    data class NoMatches(val input: String) : PlayerEvent

    /** A load failed; [message] is a safe, user-facing reason when Lavaplayer provides one. */
    data class LoadFailed(val message: String?) : PlayerEvent

    /**
     * The provider failed while producing frames. The engine treats the current track as ended
     * and advances the queue; adapters can report the failure instead of waiting forever.
     */
    data class PlaybackFailed(val message: String?) : PlayerEvent

    /** A track started playing. Retried tracks do not emit this event. */
    data class TrackStarted(val track: PlayerTrack) : PlayerEvent

    /**
     * A track ended. [queueEmpty] is true when no track remains, so adapters can schedule their
     * own leave/disconnect policy without polling.
     */
    data class TrackEnded(val queueEmpty: Boolean) : PlayerEvent

    /** Queue/current/pause state changed; adapters can refresh their presentation. */
    data class QueueChanged(val snapshot: PlayerSnapshot) : PlayerEvent

    /** The engine was explicitly stopped, including a disconnect request. */
    data object Stopped : PlayerEvent
}
