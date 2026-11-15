package es.wokis.player.engine.model

/**
 * Gateway-neutral immutable snapshot of one playable track.
 *
 * The shared engine never exposes Lavaplayer types publicly: adapters receive this value and can
 * render titles, durations, artwork and playback position without depending on the audio library.
 * [positionMillis] is captured when the snapshot is created; it is not live.
 *
 * [customName] and [customFavicon] carry the radio/TTS presentation overrides that the Kord
 * adapter used to keep in its `TrackBO`.
 */
data class PlayerTrack(
    val identifier: String,
    val title: String,
    val author: String,
    val durationMillis: Long = UNKNOWN_DURATION,
    val uri: String? = null,
    val artworkUrl: String? = null,
    val isStream: Boolean = false,
    val sourceName: String? = null,
    val customName: String? = null,
    val customFavicon: String? = null,
    val positionMillis: Long = 0L,
) {

    /** Display name used by the existing player/queue embeds. */
    val displayName: String
        get() = when {
            customName != null -> customName
            title.contains(" - ") -> title
            else -> "$author - $title"
        }

    companion object {
        /** Lavaplayer's `DURATION_MS_UNKNOWN`, kept neutral for callers. */
        const val UNKNOWN_DURATION: Long = Long.MAX_VALUE
    }
}
