package es.wokis.jda.command

import es.wokis.commands.neutral.CommandDefinition
import es.wokis.commands.neutral.GuildCommandRegistrar
import es.wokis.commands.neutral.StringOptionDefinition
import es.wokis.commands.neutral.SubcommandDefinition
import es.wokis.commands.neutral.SubcommandGroupDefinition
import es.wokis.domain.locale.LocaleCode
import es.wokis.domain.model.GuildId
import es.wokis.domain.model.UserId
import es.wokis.jda.awaitRestActionCompletion
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.interactions.DiscordLocale
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData
import net.dv8tion.jda.api.interactions.commands.build.SubcommandGroupData

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
            commandData.addOptions(option.toOptionData())
        }
        if (command.subcommands.isNotEmpty()) {
            commandData.addSubcommands(command.subcommands.map { it.toJdaData() })
        }
        if (command.subcommandGroups.isNotEmpty()) {
            commandData.addSubcommandGroups(command.subcommandGroups.map { it.toJdaData() })
        }

        // Always explicit: JDA keeps the previous default permissions when the field is omitted.
        commandData.setDefaultPermissions(
            if (command.adminOnly) {
                DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR)
            } else {
                DefaultMemberPermissions.ENABLED
            },
        )

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

/** One narrow string option, mapped to the gateway's option shape. */
private fun StringOptionDefinition.toOptionData(): OptionData {
    val optionData = OptionData(OptionType.STRING, name, description)
        .setRequired(required)
        .setAutoComplete(autocomplete)
    descriptionLocalizations.toJdaLocalizationsOrNull()?.let(optionData::setDescriptionLocalizations)
    return optionData
}

/** A leaf subcommand and its optional string option. */
private fun SubcommandDefinition.toJdaData(): SubcommandData {
    val subcommandData = SubcommandData(name, description)
    descriptionLocalizations.toJdaLocalizationsOrNull()?.let(subcommandData::setDescriptionLocalizations)
    stringOption?.let { option -> subcommandData.addOptions(option.toOptionData()) }
    return subcommandData
}

/** A subcommand group with its leaf subcommands; gateways allow no deeper nesting. */
private fun SubcommandGroupDefinition.toJdaData(): SubcommandGroupData {
    val groupData = SubcommandGroupData(name, description)
    descriptionLocalizations.toJdaLocalizationsOrNull()?.let(groupData::setDescriptionLocalizations)
    groupData.addSubcommands(subcommands.map { it.toJdaData() })
    return groupData
}

/** Rejects ids above [Long.MAX_VALUE] instead of wrapping to a different guild, and keeps the error message value-free. */
internal fun GuildId.toJdaId(): Long {
    require(value <= Long.MAX_VALUE.toULong()) {
        "Guild id does not fit in JDA's signed 64-bit id range"
    }
    return value.toLong()
}

/** Rejects ids above [Long.MAX_VALUE] instead of wrapping to a different user, and keeps the error message value-free. */
internal fun UserId.toJdaId(): Long {
    require(value <= Long.MAX_VALUE.toULong()) {
        "User id does not fit in JDA's signed 64-bit id range"
    }
    return value.toLong()
}
