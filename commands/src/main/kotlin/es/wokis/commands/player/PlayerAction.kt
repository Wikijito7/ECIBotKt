package es.wokis.commands.player

/** Actions accepted by `/player`; parsing is case-insensitive and blank means "no action". */
enum class PlayerAction {
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

        fun parse(raw: String): PlayerAction? {
            val normalized = raw.trim().lowercase()
            return entries.firstOrNull { it.name.lowercase() == normalized }
        }
    }
}
