package es.wokis.jda.player

/**
 * Outcome of the load-then-connect sequence used by `/jda-play`.
 *
 * The voice connection is only opened once a track is active, so the audio stream is never created
 * against an engine whose playback already terminated (an initial `Ended` stream would exhaust the
 * send handler before the first frame).
 */
internal sealed interface JdaPlayerPlaybackStart {

    /** A track became active within the bounded wait; the caller may connect. */
    data object Started : JdaPlayerPlaybackStart

    /** No active track and no failure was observed in time; nothing was connected. */
    data object TimedOut : JdaPlayerPlaybackStart

    /**
     * The provider reported no match or a load failure.
     *
     * [reason] must be an already-sanitized, user-facing sentence: it is returned to Discord
     * verbatim, so raw provider/Lavaplayer text is not allowed here.
     */
    data class Failed(val reason: String) : JdaPlayerPlaybackStart
}
