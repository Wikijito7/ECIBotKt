package es.wokis.jda.player

import es.wokis.domain.model.ChannelId
import es.wokis.jda.awaitRestAction
import es.wokis.jda.awaitRestActionCompletion
import es.wokis.message.MessageView
import es.wokis.player.PlayerMessageId
import es.wokis.player.PlayerMessagePort
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.exceptions.ObfuscatedChannelException
import org.slf4j.LoggerFactory

/** [PlayerMessagePort] backed by JDA; plain text keeps the single-content REST path. */
internal class JdaPlayerMessagePort(
    private val jda: JDA,
) : PlayerMessagePort {

    override suspend fun send(channelId: ChannelId, view: MessageView): PlayerMessageId {
        val channel = requireTextChannel(channelId)
        val message = if (view.isTextOnly) {
            channel.sendMessage(requireText(view)).awaitRestAction()
        } else {
            channel.sendMessage(JdaMessageViewMapper.toCreateData(view)).awaitRestAction()
        }
        return PlayerMessageId(message.idLong.toULong())
    }

    override suspend fun edit(channelId: ChannelId, messageId: PlayerMessageId, view: MessageView) {
        val channel = requireTextChannel(channelId)
        if (view.isTextOnly) {
            channel.editMessageById(messageId.toJdaId(), requireText(view))
        } else {
            channel.editMessageById(messageId.toJdaId(), JdaMessageViewMapper.toEditData(view))
        }.awaitRestActionCompletion()
    }

    override suspend fun delete(channelId: ChannelId, messageId: PlayerMessageId) {
        requireTextChannel(channelId)
            .deleteMessageById(messageId.toJdaId())
            .awaitRestActionCompletion()
    }

    private fun requireTextChannel(channelId: ChannelId): TextChannel {
        val channel = jda.getTextChannelById(channelId.toJdaId())
            ?: error("The requested player text channel is not available to the bot")
        if (channel.isObfuscated) {
            LOG.warn(
                "A player text channel is hidden from the bot ({})",
                ObfuscatedChannelException::class.simpleName,
            )
            throw ObfuscatedChannelException(channel)
        }
        return channel
    }

    private fun requireText(view: MessageView): String =
        requireNotNull(view.content?.takeIf(String::isNotBlank)) {
            "A text-only player message requires non-blank content"
        }

    private companion object {
        private val LOG = LoggerFactory.getLogger(JdaPlayerMessagePort::class.java)
    }
}

private fun ChannelId.toJdaId(): Long = value.toJdaLong("Channel id")

private fun PlayerMessageId.toJdaId(): Long = value.toJdaLong("Message id")

private fun ULong.toJdaLong(label: String): Long {
    require(this <= Long.MAX_VALUE.toULong()) {
        "$label does not fit in JDA's signed 64-bit id range"
    }
    return toLong()
}
