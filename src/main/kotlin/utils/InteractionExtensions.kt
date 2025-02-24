package es.wokis.utils

import dev.kord.core.Kord
import dev.kord.core.behavior.channel.BaseVoiceChannelBehavior
import dev.kord.core.entity.interaction.ApplicationCommandInteraction
import dev.kord.core.entity.interaction.ChatInputCommandInteraction

suspend fun ApplicationCommandInteraction.getMemberVoiceChannel(bot: Kord): BaseVoiceChannelBehavior? = data.guildId.value?.let { guildId ->
    bot.getGuildOrNull(guildId)?.let { guild ->
        guild.getMemberOrNull(user.id)?.let { member ->
            member.getVoiceStateOrNull()?.getChannelOrNull()
        }
    }
}
