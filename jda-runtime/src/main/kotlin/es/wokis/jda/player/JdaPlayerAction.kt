package es.wokis.jda.player

/**
 * Supported `/jda-player` actions.
 *
 * The set is deliberately narrow: it is the minimum needed to drive the real player during the
 * staging matrix. Reload, configuration get/set and event-stream commands are explicitly out of
 * scope for this slice.
 */
internal enum class JdaPlayerAction {
    /** Reports the current track and the pending queue. */
    QUEUE,

    /** Pauses the current track at its position. */
    PAUSE,

    /** Resumes the current track from its paused position. */
    RESUME,

    /** Stops the current track and advances the queue. */
    SKIP,

    /** Clears the queue, stops playback and lets the adapter close the connection. */
    STOP,

    /** Clears the queue and disconnects from voice, keeping the runtime reusable. */
    DISCONNECT,

    /** Rejoins the caller's voice channel, preserving the current track and queue. */
    RECONNECT;

    companion object {
        /** The accepted actions, in the order shown to the caller. */
        val names: List<String> = entries.map { it.name.lowercase() }

        /** Parses a case-insensitive action name, or returns `null` when it is not supported. */
        fun parse(raw: String): JdaPlayerAction? {
            val normalized = raw.trim().lowercase()
            return entries.firstOrNull { it.name.lowercase() == normalized }
        }
    }
}
