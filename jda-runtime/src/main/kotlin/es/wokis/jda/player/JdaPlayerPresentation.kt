package es.wokis.jda.player

import es.wokis.player.engine.event.PlayerEvent

/**
 * Event-derived presentation for one guild's voice adapter.
 *
 * [revision] lets a caller wait only for later transitions. [loadFailure] maps events to fixed
 * reasons and [toString] is redacted, because provider text can embed diagnostics.
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

    val loadFailure: String?
        get() = when (lastEvent) {
            is PlayerEvent.NoMatches -> NO_MATCHES_REASON
            is PlayerEvent.LoadFailed -> LOAD_REJECTED_REASON
            is PlayerEvent.PlaybackFailed -> PLAYBACK_FAILED_REASON
            else -> null
        }

    private companion object {
        const val NO_MATCHES_REASON = "No tracks matched the supplied input."
        const val LOAD_REJECTED_REASON =
            "The provider rejected the load; check the input and try again."
        const val PLAYBACK_FAILED_REASON =
            "The provider could not play that track; the queue moved on."
    }
}
