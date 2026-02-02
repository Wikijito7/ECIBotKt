package services.player.result

import dev.kord.core.entity.Message
import dev.kord.core.entity.channel.TextChannel

data class PlayerChannelResult(
    val message: Message,
    val channel: TextChannel,
    val isNewChannel: Boolean
)