package es.wokis.player

import es.wokis.domain.model.ChannelId

/** Platform-neutral player-message id carrying the raw unsigned 64-bit snowflake. */
@JvmInline
value class PlayerMessageId(val value: ULong) {

    override fun toString(): String = value.toString()
}

/**
 * Gateway-neutral, text-only port for the player message.
 *
 * Implementations act on exactly the supplied message and channel, never a fallback channel.
 * Rich embeds and components stay in a later phase.
 */
interface PlayerMessagePort {

    /** Sends [content] as a plain text message and returns its neutral identifier. */
    suspend fun send(channelId: ChannelId, content: String): PlayerMessageId

    /** Replaces the plain text of an existing player message. */
    suspend fun edit(channelId: ChannelId, messageId: PlayerMessageId, content: String)

    /** Deletes an existing player message. */
    suspend fun delete(channelId: ChannelId, messageId: PlayerMessageId)
}
