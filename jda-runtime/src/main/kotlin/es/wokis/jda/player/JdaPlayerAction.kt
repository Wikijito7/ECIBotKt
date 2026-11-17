package es.wokis.jda.player

internal enum class JdaPlayerAction {
    QUEUE,

    PAUSE,

    RESUME,

    SKIP,

    /** Clears the queue, stops playback and lets the adapter close the connection. */
    STOP,

    /** Clears the queue and disconnects from voice, keeping the runtime reusable. */
    DISCONNECT,

    /** Leaves and rejoins the caller's voice channel when a session exists, preserving the current track and queue. */
    RECONNECT;

    companion object {
        val names: List<String> = entries.map { it.name.lowercase() }

        fun parse(raw: String): JdaPlayerAction? {
            val normalized = raw.trim().lowercase()
            return entries.firstOrNull { it.name.lowercase() == normalized }
        }
    }
}
