package es.wokis.jda.player

import es.wokis.commands.neutral.CommandContext
import es.wokis.commands.player.PlayerVoiceTarget
import es.wokis.commands.player.PlayerVoiceTargetPort
import es.wokis.commands.player.PlayerVoiceTargetResult
import es.wokis.commands.player.PlayerVoiceUnavailableReason
import es.wokis.domain.model.ChannelId
import es.wokis.jda.command.toJdaId
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel

/** [PlayerVoiceTargetPort] backed by JDA's guild/member cache. */
internal class JdaPlayerVoiceTargetPort(
    private val jda: JDA,
) : PlayerVoiceTargetPort {

    override suspend fun resolve(context: CommandContext): PlayerVoiceTargetResult {
        val guild = context.guildId?.let { guildId -> jda.getGuildById(guildId.toJdaId()) }
            ?: return PlayerVoiceTargetResult.Unavailable(PlayerVoiceUnavailableReason.NO_GUILD)
        val channel = guild.getMemberById(context.userId.toJdaId())?.voiceState?.channel as? VoiceChannel
            ?: return PlayerVoiceTargetResult.Unavailable(PlayerVoiceUnavailableReason.NO_VOICE_CHANNEL)
        return PlayerVoiceTargetResult.Ready(
            PlayerVoiceTarget(
                channelId = ChannelId(channel.idLong.toULong()),
                channelName = channel.name,
            ),
        )
    }

    override suspend fun channelName(channelId: ChannelId): String? =
        channelId.value
            .takeIf { it <= Long.MAX_VALUE.toULong() }
            ?.let { jda.getVoiceChannelById(it.toLong())?.name }
}
