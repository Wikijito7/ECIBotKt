package es.wokis.utils

import dev.kord.core.Kord
import dev.kord.core.behavior.channel.BaseVoiceChannelBehavior
import dev.kord.core.entity.interaction.ApplicationCommandInteraction
import dev.kord.core.entity.interaction.ChatInputCommandInteraction
import dev.kord.core.entity.interaction.Interaction
import kotlin.jvm.Throws

suspend fun ApplicationCommandInteraction.getMemberVoiceChannel(bot: Kord): BaseVoiceChannelBehavior? = data.guildId.value?.let { guildId ->
    bot.getGuildOrNull(guildId)?.let { guild ->
        guild.getMemberOrNull(user.id)?.let { member ->
            member.getVoiceStateOrNull()?.getChannelOrNull()
        }
    }
}

fun ChatInputCommandInteraction.getArgument(
    argumentName: String
): String? = command.strings[argumentName]?.takeIfNotEmpty()

@Throws(IllegalArgumentException::class)
suspend fun Interaction.getGuildName(): String =
    data.guildId.value?.let { kord.getGuild(it) }?.name ?: throw IllegalArgumentException("guild id is null")

