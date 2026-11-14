package es.wokis.jda.command

import es.wokis.commands.neutral.CommandContext
import es.wokis.domain.locale.DiscordLocaleCatalog
import es.wokis.domain.locale.LocaleCode
import es.wokis.domain.model.ChannelId
import es.wokis.domain.model.GuildId
import es.wokis.domain.model.UserId
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.DiscordLocale
import net.dv8tion.jda.api.interactions.commands.OptionType

/**
 * Maps a JDA slash-command event to the neutral command context.
 *
 * This is the only place where JDA identifiers and locales are translated, keeping the
 * command boundary free of gateway types. The guild default locale and the invoking client's
 * locale are mapped separately so a user preference is never mistaken for the guild default.
 */
class JdaCommandContextMapper {

    fun map(event: SlashCommandInteractionEvent): CommandContext {
        val guildLocale = event.guild?.locale.toLocaleCodeOrNull()
        val userLocale = event.userLocale.toLocaleCodeOrNull()

        return CommandContext(
            commandName = event.name,
            guildId = event.guild?.idLong?.toGuildId(),
            channelId = ChannelId(event.channel.idLong.toULong()),
            userId = UserId(event.user.idLong.toULong()),
            locale = guildLocale ?: userLocale,
            guildLocale = guildLocale,
            userLocale = userLocale,
            stringOptions = event.options
                .filter { it.type == OptionType.STRING }
                .associate { it.name to it.asString },
        )
    }
}

private fun Long.toGuildId(): GuildId = GuildId(toULong())

/**
 * Maps a JDA locale enum through its canonical `.locale` string and validates it against the
 * shared catalog. Enum names and localized names are never persisted, and a future JDA tag that
 * the catalog does not know yet maps to `null` instead of becoming a stored custom tag.
 */
internal fun DiscordLocale?.toLocaleCodeOrNull(): LocaleCode? = when {
    this == null || this == DiscordLocale.UNKNOWN -> null
    else -> DiscordLocaleCatalog.find(locale)
}
