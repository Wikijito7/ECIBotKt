package es.wokis.utils

import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.core.entity.interaction.GlobalChatInputCommandInteraction

suspend fun GlobalChatInputCommandInteraction.getVoiceChannelId(bot: Kord): Snowflake? = data.guildId.value?.let { guildId ->
    bot.getGuildOrNull(guildId)?.let { guild ->
        guild.getMemberOrNull(user.id)?.let { member ->
            member.getVoiceStateOrNull()?.channelId
        }
    }
}