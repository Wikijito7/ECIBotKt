package es.wokis.jda.player

/** Outcome of waiting for the adapter to report a track or a load failure. */
internal sealed interface JdaPlayerPlaybackStart {

    data object Started : JdaPlayerPlaybackStart

    data class Failed(val failure: JdaPlayerLoadFailure) : JdaPlayerPlaybackStart

    data object TimedOut : JdaPlayerPlaybackStart
}
