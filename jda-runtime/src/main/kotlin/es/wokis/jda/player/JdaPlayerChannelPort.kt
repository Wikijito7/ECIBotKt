package es.wokis.jda.player

import es.wokis.commands.player.PlayerChannelPort
import es.wokis.commands.player.PlayerChannelSetup
import es.wokis.message.MessageView
import net.dv8tion.jda.api.entities.Guild

/** [PlayerChannelPort] backed by JDA; the REST details stay in [JdaPlayerChannelService]. */
internal class JdaPlayerChannelPort(
    private val guild: Guild,
    private val service: JdaPlayerChannelService = JdaPlayerChannelService(),
) : PlayerChannelPort {

    override suspend fun setUp(view: MessageView): PlayerChannelSetup {
        val result = service.setUp(guild, view) ?: return PlayerChannelSetup.Failed
        return if (result.isNewChannel) {
            PlayerChannelSetup.Created(result.channelId, result.messageId)
        } else {
            PlayerChannelSetup.Reused(result.channelId, result.messageId)
        }
    }
}
