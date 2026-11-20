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
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.interactions.DiscordLocale
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.CommandData
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData
import net.dv8tion.jda.api.interactions.commands.build.SubcommandGroupData
import org.slf4j.LoggerFactory

private val LOG = LoggerFactory.getLogger("es.wokis.jda.command.JdaCommandRegistration")

/**
 * [GuildCommandRegistrar] that collects the guild-agnostic command definitions instead of publishing
 * them: the guild id is ignored, definitions are deduplicated by name, and two different definitions
 * for the same name fail loudly at boot.
 */
internal class CollectingCommandRegistrar : GuildCommandRegistrar {

    private val definitionsByName = LinkedHashMap<String, CommandDefinition>()

    override suspend fun register(guildId: GuildId, command: CommandDefinition) {
        val existing = definitionsByName.putIfAbsent(command.name, command)
        check(existing == null || existing == command) {
            "Conflicting definitions collected for /${command.name}"
        }
    }

    fun definitions(): List<CommandDefinition> = definitionsByName.values.toList()
}

/** Publishes [definitions] as the complete global command set in one bulk overwrite. */
internal suspend fun registerGlobalCommands(jda: JDA, definitions: List<CommandDefinition>) {
    jda.updateCommands()
        .addCommands(definitions.map { it.toJdaCommandData() })
        .awaitRestActionCompletion()
    LOG.info("Published {} global application command(s)", definitions.size)
}

/** Empties each guild's command set, removing any guild-scoped shadow of the global set. */
internal suspend fun purgeGuildCommands(guilds: List<Guild>) {
    guilds.forEach { guild -> guild.updateCommands().awaitRestActionCompletion() }
    LOG.info("Purged guild-scoped commands in {} guild(s)", guilds.size)
}

/** Maps a neutral definition onto the JDA command shape; the permission set is always explicit. */
internal fun CommandDefinition.toJdaCommandData(): CommandData {
    val commandData = Commands.slash(name, description)
    descriptionLocalizations.toJdaLocalizationsOrNull()?.let { localizations ->
        commandData.setDescriptionLocalizations(localizations)
    }

    stringOption?.let { option ->
        commandData.addOptions(option.toOptionData())
    }
    if (subcommands.isNotEmpty()) {
        commandData.addSubcommands(subcommands.map { it.toJdaData() })
    }
    if (subcommandGroups.isNotEmpty()) {
        commandData.addSubcommandGroups(subcommandGroups.map { it.toJdaData() })
    }

    // Always explicit: JDA keeps the previous default permissions when the field is omitted.
    commandData.setDefaultPermissions(
        if (adminOnly) {
            DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR)
        } else {
            DefaultMemberPermissions.ENABLED
        },
    )

    return commandData
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
