package es.wokis.jda.player

import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel

/** Resolved voice target; [Unavailable] carries a safe user-facing message. */
internal sealed interface JdaPlayerVoiceTarget {

    data class Ready(val guild: Guild, val channel: VoiceChannel) : JdaPlayerVoiceTarget

    data class Unavailable(val message: String) : JdaPlayerVoiceTarget
}
