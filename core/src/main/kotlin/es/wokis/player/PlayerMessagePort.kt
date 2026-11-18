package es.wokis.player

import es.wokis.domain.model.ChannelId
import es.wokis.message.MessageView

/** Platform-neutral player-message id carrying the raw unsigned 64-bit snowflake. */
@JvmInline
value class PlayerMessageId(val value: ULong) {

    override fun toString(): String = value.toString()
}

/**
 * Gateway-neutral port for the player message.
 *
 * Implementations act on exactly the supplied message and channel, never a fallback channel.
 * Plain-text calls delegate to the view overload so every adapter renders one message shape.
 */
interface PlayerMessagePort {

    /** Sends [content] as a plain text message and returns its neutral identifier. */
    suspend fun send(channelId: ChannelId, content: String): PlayerMessageId =
        send(channelId, MessageView(content = content))

    /** Sends [view] and returns the neutral identifier of the created message. */
    suspend fun send(channelId: ChannelId, view: MessageView): PlayerMessageId

    /** Replaces the content of an existing player message with [content]. */
    suspend fun edit(channelId: ChannelId, messageId: PlayerMessageId, content: String) =
        edit(channelId, messageId, MessageView(content = content))

    /** Replaces the content of an existing player message with [view]. */
    suspend fun edit(channelId: ChannelId, messageId: PlayerMessageId, view: MessageView)

    /** Deletes an existing player message. */
    suspend fun delete(channelId: ChannelId, messageId: PlayerMessageId)
}
