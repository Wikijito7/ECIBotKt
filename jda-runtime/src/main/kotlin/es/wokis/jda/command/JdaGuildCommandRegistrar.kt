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
 * Guild-scoped [GuildCommandRegistrar]: each call targets its own guild only, and an unavailable guild
 * fails instead of falling back or registering globally, so no command leaks to the global scope.
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

/** Maps only known JDA locales; unknown or custom tags are dropped and never fabricated or guessed. */
private fun Map<LocaleCode, String>.toJdaLocalizationsOrNull(): Map<DiscordLocale, String>? =
    mapNotNull { (localeCode, value) ->
        DiscordLocale.from(localeCode.value)
            .takeIf { it != DiscordLocale.UNKNOWN }
            ?.let { it to value }
    }.toMap().takeIf { it.isNotEmpty() }

/** Rejects ids above [Long.MAX_VALUE] instead of wrapping to a different guild, and keeps the error message value-free. */
internal fun GuildId.toJdaId(): Long {
    require(value <= Long.MAX_VALUE.toULong()) {
        "Guild id does not fit in JDA's signed 64-bit id range"
    }
    return value.toLong()
}
