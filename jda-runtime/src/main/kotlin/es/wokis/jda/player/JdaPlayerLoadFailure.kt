package es.wokis.jda.player

/** Safe provider-failure categories; provider text is never carried further. */
internal enum class JdaPlayerLoadFailure {
    NO_MATCHES,
    LOAD_REJECTED,
    PLAYBACK_FAILED,
}
