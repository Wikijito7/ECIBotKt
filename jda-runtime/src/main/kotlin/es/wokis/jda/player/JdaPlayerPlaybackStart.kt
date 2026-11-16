package es.wokis.jda.player

/**
 * Result of load-then-connect: voice opens only after a track is active, so an already-ended
 * stream cannot exhaust the send handler.
 */
internal sealed interface JdaPlayerPlaybackStart {

    data object Started : JdaPlayerPlaybackStart

    data object TimedOut : JdaPlayerPlaybackStart

    /** [reason] is returned to Discord verbatim, so raw provider text must never be placed here. */
    data class Failed(val reason: String) : JdaPlayerPlaybackStart
}
