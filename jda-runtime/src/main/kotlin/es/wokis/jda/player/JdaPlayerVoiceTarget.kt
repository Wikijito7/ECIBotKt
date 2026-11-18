package es.wokis.jda.player

import es.wokis.localization.PlayerMessageKeys
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel

/** Resolved voice target; [Unavailable] carries a localized message key. */
internal sealed interface JdaPlayerVoiceTarget {

    data class Ready(val guild: Guild, val channel: VoiceChannel) : JdaPlayerVoiceTarget

    data class Unavailable(val reason: JdaPlayerUnavailableReason) : JdaPlayerVoiceTarget
}

/** Why the caller has no usable voice target. */
internal enum class JdaPlayerUnavailableReason(val key: String) {
    NO_GUILD(PlayerMessageKeys.ERROR_NO_GUILD),
    NO_VOICE_CHANNEL(PlayerMessageKeys.ERROR_NO_VOICE_CHANNEL),
}
