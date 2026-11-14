package es.wokis.player

import es.wokis.domain.model.ChannelId

/**
 * Platform-neutral identifier of a player message sent by the bot.
 *
 * Keeps the raw unsigned 64-bit snowflake without exposing a gateway-library type.
 */
@JvmInline
value class PlayerMessageId(val value: ULong) {

    override fun toString(): String = value.toString()
}

/**
 * Gateway-neutral, text-only port for the player message.
 *
 * Phase 2C deliberately exposes plain text only: rich player embeds and message components
 * stay in a later phase. Implementations must send, edit and delete exactly the message
 * identified by the supplied values and must not fall back to another channel.
 */
interface PlayerMessagePort {

    /** Sends [content] as a plain text message and returns its neutral identifier. */
    suspend fun send(channelId: ChannelId, content: String): PlayerMessageId

    /** Replaces the plain text of an existing player message. */
    suspend fun edit(channelId: ChannelId, messageId: PlayerMessageId, content: String)

    /** Deletes an existing player message. */
    suspend fun delete(channelId: ChannelId, messageId: PlayerMessageId)
}
