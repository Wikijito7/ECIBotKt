package es.wokis.jda.player

import es.wokis.player.engine.event.PlayerEvent

/**
 * Event-derived presentation for one guild's voice adapter.
 *
 * [revision] lets a caller wait only for later transitions. [loadFailure] maps events to fixed
 * categories and [toString] is redacted, because provider text can embed diagnostics.
 */
internal data class JdaPlayerPresentation(
    val revision: Long = 0,
    val activeTrack: Boolean = false,
    val paused: Boolean = false,
    val queueSize: Int = 0,
    val lastEvent: PlayerEvent? = null,
) {

    override fun toString(): String =
        "JdaPlayerPresentation(revision=$revision, activeTrack=$activeTrack, paused=$paused, " +
            "queueSize=$queueSize, loadFailure=$loadFailure)"

    val loadFailure: JdaPlayerLoadFailure?
        get() = when (lastEvent) {
            is PlayerEvent.NoMatches -> JdaPlayerLoadFailure.NO_MATCHES
            is PlayerEvent.LoadFailed -> JdaPlayerLoadFailure.LOAD_REJECTED
            is PlayerEvent.PlaybackFailed -> JdaPlayerLoadFailure.PLAYBACK_FAILED
            else -> null
        }
}
