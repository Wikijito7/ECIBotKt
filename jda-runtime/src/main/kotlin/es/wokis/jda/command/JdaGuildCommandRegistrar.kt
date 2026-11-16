package es.wokis.jda.command

import es.wokis.commands.neutral.CommandDefinition
import es.wokis.commands.neutral.GuildCommandRegistrar
import es.wokis.domain.locale.LocaleCode
import es.wokis.domain.model.GuildId
import es.wokis.jda.awaitRestActionCompletion
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.interactions.DiscordLocale
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.build.OptionData

/**
 * [GuildCommandRegistrar] backed by a live JDA client.
 *
 * The only registration route is the guild-scoped `guild.upsertCommand(...)` call on the guild
 * resolved from the validated [GuildId]. Commands are never published application-wide, so a
 * wrong guild fails fast instead of leaking commands to every guild the bot is in.
 */
internal class JdaGuildCommandRegistrar(
    private val jda: JDA,
) : GuildCommandRegistrar {

    override suspend fun register(guildId: GuildId, command: CommandDefinition) {
        val guild = jda.getGuildById(guildId.toJdaId())
            ?: error(
                "The bot is not a member of guild $guildId, so /${command.name} cannot be registered",
            )

        val commandData = Commands.slash(command.name, command.description)
        command.descriptionLocalizations.toJdaLocalizationsOrNull()?.let { localizations ->
            commandData.setDescriptionLocalizations(localizations)
        }

        command.stringOption?.let { option ->
            val optionData = OptionData(OptionType.STRING, option.name, option.description)
                .setRequired(option.required)
                .setAutoComplete(option.autocomplete)
            option.descriptionLocalizations.toJdaLocalizationsOrNull()?.let { localizations ->
                optionData.setDescriptionLocalizations(localizations)
            }
            commandData.addOptions(optionData)
        }

        if (command.adminOnly) {
            commandData.setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR))
        }

        guild.upsertCommand(commandData)
            .awaitRestActionCompletion()
    }
}

/**
 * Maps neutral canonical tags to JDA locales only at the registration edge.
 *
 * Translations that do not correspond to a real JDA/Discord locale are dropped instead of being
 * fabricated or sent as custom tags. An empty result means the definition carries no metadata
 * beyond its base description.
 */
private fun Map<LocaleCode, String>.toJdaLocalizationsOrNull(): Map<DiscordLocale, String>? =
    mapNotNull { (localeCode, value) ->
        DiscordLocale.from(localeCode.value)
            .takeIf { it != DiscordLocale.UNKNOWN }
            ?.let { it to value }
    }.toMap().takeIf { it.isNotEmpty() }

/**
 * Converts the unsigned neutral [GuildId] into JDA's signed 64-bit guild id.
 *
 * Discord snowflakes fit in the signed range in practice. A value above [Long.MAX_VALUE] is
 * rejected instead of wrapping around, because the wrapped id would silently address a
 * different guild and register the command there. The message is value-free, consistent with
 * every other snowflake range check, so the rejected id is not echoed.
 */
internal fun GuildId.toJdaId(): Long {
    require(value <= Long.MAX_VALUE.toULong()) {
        "Guild id does not fit in JDA's signed 64-bit id range"
    }
    return value.toLong()
}
