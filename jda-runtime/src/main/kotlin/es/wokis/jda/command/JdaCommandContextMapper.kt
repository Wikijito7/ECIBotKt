package es.wokis.jda.command

import es.wokis.commands.neutral.CommandContext
import es.wokis.domain.locale.LocaleCode
import es.wokis.domain.model.ChannelId
import es.wokis.domain.model.GuildId
import es.wokis.domain.model.UserId
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.DiscordLocale

/**
 * Maps a JDA slash-command event to the neutral command context.
 *
 * This is the only place where JDA identifiers and locales are translated, keeping the
 * command boundary free of gateway types.
 */
class JdaCommandContextMapper {

    fun map(event: SlashCommandInteractionEvent): CommandContext = CommandContext(
        commandName = event.name,
        guildId = event.guild?.idLong?.toGuildId(),
        channelId = ChannelId(event.channel.idLong.toULong()),
        userId = UserId(event.user.idLong.toULong()),
        locale = event.guild?.locale.toLocaleCodeOrNull() ?: event.userLocale.toLocaleCodeOrNull(),
    )
}

private fun Long.toGuildId(): GuildId = GuildId(toULong())

private fun DiscordLocale?.toLocaleCodeOrNull(): LocaleCode? = when {
    this == null || this == DiscordLocale.UNKNOWN -> null
    else -> LocaleCode(locale)
}
