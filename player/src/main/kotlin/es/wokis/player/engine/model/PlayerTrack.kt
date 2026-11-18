package es.wokis.player.engine.model

/**
 * Gateway-neutral immutable snapshot of one playable track.
 *
 * The engine never exposes Lavaplayer types publicly. [positionMillis] is captured at snapshot
 * creation, not live; [customName] and [customFavicon] carry radio/TTS presentation overrides.
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
