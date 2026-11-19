package es.wokis.commands.player

import es.wokis.commands.neutral.CommandContext
import es.wokis.domain.model.ChannelId
import es.wokis.localization.PlayerMessageKeys

/** The caller's voice channel as the gateway resolved it, with its current name. */
data class PlayerVoiceTarget(
    val channelId: ChannelId,
    val channelName: String,
)

/** Why the caller has no usable voice target. */
enum class PlayerVoiceUnavailableReason(val key: String) {
    NO_GUILD(PlayerMessageKeys.ERROR_NO_GUILD),
    NO_VOICE_CHANNEL(PlayerMessageKeys.ERROR_NO_VOICE_CHANNEL),
}

sealed interface PlayerVoiceTargetResult {

    data class Ready(val target: PlayerVoiceTarget) : PlayerVoiceTargetResult

    data class Unavailable(val reason: PlayerVoiceUnavailableReason) : PlayerVoiceTargetResult
}

/**
 * Gateway-neutral resolver for the caller's voice channel.
 *
 * The guild and member lookup stays in the adapter. [channelName] reads the channel's current name,
 * or `null` when the channel no longer exists.
 */
interface PlayerVoiceTargetPort {

    suspend fun resolve(context: CommandContext): PlayerVoiceTargetResult

    suspend fun channelName(channelId: ChannelId): String?
}
