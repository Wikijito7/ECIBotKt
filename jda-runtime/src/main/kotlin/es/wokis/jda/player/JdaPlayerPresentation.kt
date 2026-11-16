package es.wokis.jda.player

import es.wokis.player.engine.event.PlayerEvent

/**
 * Event-derived player presentation for one guild's JDA voice adapter.
 *
 * The adapter is the **only** collector of [es.wokis.player.engine.PlayerEngine.events], so it is
 * also the only place that can observe playback transitions. Commands and diagnostics read this
 * immutable state instead of collecting the single-subscriber engine stream themselves.
 *
 * [revision] increases with every observed event, which lets a caller arm a watch before issuing a
 * load and then wait only for transitions that happened afterwards. [loadFailure] renders the last
 * load failure in a user-facing way; it is `null` for every other event.
 *
 * **No provider text is exposed.** The reason carried by [PlayerEvent.LoadFailed] and
 * [PlayerEvent.PlaybackFailed] comes from Lavaplayer/provider errors and can embed a URL, an HTTP
 * status body or other diagnostics. It is never echoed to Discord and never logged: [loadFailure]
 * returns one fixed reason per event family, and [toString] is redacted so rendering the whole
 * presentation cannot print the raw [lastEvent] either.
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

    /** Safe user-facing reason when the latest load matched nothing or failed, otherwise `null`. */
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
