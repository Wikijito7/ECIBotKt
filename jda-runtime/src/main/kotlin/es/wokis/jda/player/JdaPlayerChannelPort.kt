package es.wokis.jda.player

import es.wokis.commands.player.PlayerChannelPort
import es.wokis.commands.player.PlayerChannelSetup
import es.wokis.message.MessageView
import kotlinx.coroutines.CancellationException
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.exceptions.MissingAccessException
import org.slf4j.LoggerFactory

/**
 * [PlayerChannelPort] backed by JDA; the REST details stay in [JdaPlayerChannelService].
 *
 * A hidden channel (`ObfuscatedChannelException`, a [MissingAccessException] subtype) that escapes
 * the service is reported as [PlayerChannelSetup.Failed], so the coordinator's existing fallback
 * answers with the localized `player_channel_creation_failed` warning in the invoking channel.
 */
internal class JdaPlayerChannelPort(
    private val guild: Guild,
    private val service: JdaPlayerChannelService = JdaPlayerChannelService(),
) : PlayerChannelPort {

    override suspend fun setUp(view: MessageView): PlayerChannelSetup = try {
        val result = service.setUp(guild, view) ?: return PlayerChannelSetup.Failed
        if (result.isNewChannel) {
            PlayerChannelSetup.Created(result.channelId, result.messageId)
        } else {
            PlayerChannelSetup.Reused(result.channelId, result.messageId)
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (hidden: MissingAccessException) {
        LOG.warn(
            "Could not set up the player channel: {} is hidden ({})",
            TextChannel::class.simpleName,
            hidden.javaClass.simpleName,
        )
        PlayerChannelSetup.Failed
    }

    private companion object {
        private val LOG = LoggerFactory.getLogger(JdaPlayerChannelPort::class.java)
    }
}
