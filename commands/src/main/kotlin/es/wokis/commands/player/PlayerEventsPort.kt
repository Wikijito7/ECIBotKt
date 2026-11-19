package es.wokis.commands.player

import es.wokis.player.engine.event.PlayerEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** Event-derived playback state a load waits on; [revision] orders the transitions. */
data class PlayerPlaybackState(
    val revision: Long = 0,
    val activeTrack: Boolean = false,
    val loadFailed: Boolean = false,
)

/**
 * Gateway-neutral subscription seam over one guild's player transport.
 *
 * [events] is the adapter's buffered fan-out, [playbackState] the revision-ordered presentation the
 * coordinator waits on after a load, and [connect] opens the voice session for a resolved target.
 * The gateway adapter owns the transport; this seam carries no gateway type.
 */
interface PlayerEventsPort {

    val events: Flow<PlayerEvent>

    val playbackState: StateFlow<PlayerPlaybackState>

    /** @return `false` when another voice path owns the session or the adapter is closing. */
    suspend fun connect(target: PlayerVoiceTarget): Boolean
}
