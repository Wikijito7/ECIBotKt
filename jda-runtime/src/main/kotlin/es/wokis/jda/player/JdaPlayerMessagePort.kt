package es.wokis.jda.player

import es.wokis.domain.model.ChannelId
import es.wokis.jda.awaitRestAction
import es.wokis.jda.awaitRestActionCompletion
import es.wokis.player.PlayerMessageId
import es.wokis.player.PlayerMessagePort
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel

/**
 * JDA implementation of the text-only [PlayerMessagePort].
 *
 * Only plain text is sent, edited or deleted: no embeds, components or attachments are
 * involved. The channel is always resolved from the supplied neutral [ChannelId]; there is
 * no fallback to another channel.
 */
internal class JdaPlayerMessagePort(
    private val jda: JDA,
) : PlayerMessagePort {

    override suspend fun send(channelId: ChannelId, content: String): PlayerMessageId {
        val channel = requireTextChannel(channelId)
        val message = channel.sendMessage(content).awaitRestAction()
        return PlayerMessageId(message.idLong.toULong())
    }

    override suspend fun edit(channelId: ChannelId, messageId: PlayerMessageId, content: String) {
        requireTextChannel(channelId)
            .editMessageById(messageId.toJdaId(), content)
            .awaitRestActionCompletion()
    }

    override suspend fun delete(channelId: ChannelId, messageId: PlayerMessageId) {
        requireTextChannel(channelId)
            .deleteMessageById(messageId.toJdaId())
            .awaitRestActionCompletion()
    }

    private fun requireTextChannel(channelId: ChannelId): TextChannel =
        jda.getTextChannelById(channelId.toJdaId())
            ?: throw IllegalStateException("The requested player text channel is not available to the bot")
}

private fun ChannelId.toJdaId(): Long = value.toJdaLong("Channel id")

private fun PlayerMessageId.toJdaId(): Long = value.toJdaLong("Message id")

private fun ULong.toJdaLong(label: String): Long {
    require(this <= Long.MAX_VALUE.toULong()) {
        "$label $this does not fit in JDA's signed 64-bit id range"
    }
    return toLong()
}
