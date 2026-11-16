package es.wokis.jda.player

import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel

/**
 * Result of resolving the invoking member's voice channel for a player command.
 *
 * Resolution happens in the JDA adapter (this module) because only a gateway adapter may touch
 * gateway types; the neutral command context carries ids only. [Unavailable] always carries a safe,
 * user-facing message, never a raw gateway identifier or exception.
 */
internal sealed interface JdaPlayerVoiceTarget {

    /** The invoking member is in a regular voice channel of [guild]. */
    data class Ready(val guild: Guild, val channel: VoiceChannel) : JdaPlayerVoiceTarget

    /** No usable target exists; [message] explains what the caller must do. */
    data class Unavailable(val message: String) : JdaPlayerVoiceTarget
}
