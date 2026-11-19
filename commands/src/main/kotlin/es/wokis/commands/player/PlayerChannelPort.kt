package es.wokis.commands.player

import es.wokis.domain.model.ChannelId
import es.wokis.message.MessageView
import es.wokis.player.PlayerMessageId

/** Where the player message lives after the dedicated channel was prepared. */
sealed interface PlayerChannelSetup {

    /** The channel did not exist and was created. */
    data class Created(val channelId: ChannelId, val messageId: PlayerMessageId) : PlayerChannelSetup

    /** An existing player channel was reused. */
    data class Reused(val channelId: ChannelId, val messageId: PlayerMessageId) : PlayerChannelSetup

    /** The channel could not be found or written; the coordinator falls back to the invoking channel. */
    data object Failed : PlayerChannelSetup
}

/**
 * Gateway-neutral seam over the dedicated `#player` channel.
 *
 * Implementations find or create the channel, clear its backlog and post [view] there; an unwritable
 * channel is reported through [PlayerChannelSetup.Failed] instead of an exception. The returned ids
 * are the handle later edits act on.
 */
interface PlayerChannelPort {

    suspend fun setUp(view: MessageView): PlayerChannelSetup
}
