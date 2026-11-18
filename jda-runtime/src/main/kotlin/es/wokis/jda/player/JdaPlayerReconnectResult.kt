package es.wokis.jda.player

/** Outcome of a `/jda-player reconnect` attempt; only [Reconnected] claims a live session. */
internal sealed interface JdaPlayerReconnectResult {
    data object Reconnected : JdaPlayerReconnectResult

    data object NotConnected : JdaPlayerReconnectResult

    data object ForeignSession : JdaPlayerReconnectResult

    /** Another reconnect is still waiting for JDA to confirm the previous close. */
    data object InProgress : JdaPlayerReconnectResult

    data object TimedOut : JdaPlayerReconnectResult
}
