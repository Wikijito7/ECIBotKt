package es.wokis.player.engine.event

import es.wokis.player.engine.model.PlayerSnapshot
import es.wokis.player.engine.model.PlayerTrack

/**
 * Events emitted by the serialized player engine after a state transition.
 *
 * Lavaplayer callbacks never emit directly: they enqueue a mailbox command, and the actor emits the
 * event after applying the transition, so event order matches state order.
 */
sealed interface PlayerEvent {

    data class TrackQueued(
        val track: PlayerTrack,
        val addToFront: Boolean,
        val uri: String?,
    ) : PlayerEvent

    data class PlaylistQueued(
        val name: String,
        val trackCount: Int,
        val playlistUrl: String?,
        val addToFront: Boolean,
    ) : PlayerEvent

    data class NoMatches(val input: String) : PlayerEvent

    /** A load failed; [message] is a safe, user-facing reason when Lavaplayer provides one. */
    data class LoadFailed(val message: String?) : PlayerEvent

    /**
     * The provider failed while producing frames. The engine treats the current track as ended
     * and advances the queue; adapters can report the failure instead of waiting forever.
     */
    data class PlaybackFailed(val message: String?) : PlayerEvent

    /** Retried tracks do not emit this event. */
    data class TrackStarted(val track: PlayerTrack) : PlayerEvent

    /** [queueEmpty] lets adapters schedule their own leave/disconnect policy without polling. */
    data class TrackEnded(val queueEmpty: Boolean) : PlayerEvent

    data class QueueChanged(val snapshot: PlayerSnapshot) : PlayerEvent

    /** The engine was explicitly stopped, including a disconnect request. */
    data object Stopped : PlayerEvent
}
