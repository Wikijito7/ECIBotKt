package es.wokis.commands.config

import dev.kord.core.Kord
import dev.kord.core.behavior.interaction.response.DeferredPublicMessageInteractionResponseBehavior
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.entity.interaction.AutoCompleteInteraction
import dev.kord.core.entity.interaction.ChatInputCommandInteraction
import es.wokis.commands.Autocomplete
import es.wokis.commands.CommandName
import es.wokis.commands.GroupCommand
import es.wokis.localization.LocalizationKeys
import es.wokis.services.localization.LocalizationService
import dev.kord.core.entity.interaction.SubCommand as KordSubCommand

class ConfigGroupCommand(
    private val configReloadCommand: ConfigReloadCommand,
    private val configSetCommand: ConfigSetCommand,
    private val configGetCommand: ConfigGetCommand,
    private val localizationService: LocalizationService
) : GroupCommand, Autocomplete {

    override suspend fun onRegisterCommand(kord: Kord) {
        kord.createGlobalChatInputCommand(
            CommandName.Config.commandName,
            localizationService.getString(LocalizationKeys.CONFIG_COMMAND_DESCRIPTION)
        ) {
            descriptionLocalizations = localizationService.getLocalizations(LocalizationKeys.CONFIG_COMMAND_DESCRIPTION)
            configReloadCommand.onRegisterCommand(this)
            configSetCommand.onRegisterCommand(this)
            configGetCommand.onRegisterCommand(this)
        }
    }

    override suspend fun onExecute(
        interaction: ChatInputCommandInteraction,
        response: DeferredPublicMessageInteractionResponseBehavior
    ) {
        val commandName = (interaction.command as? KordSubCommand)?.name
        commandName?.let {
            when (commandName) {
                CommandName.Config.Reload.commandName -> configReloadCommand.onExecute(interaction, response)
                CommandName.Config.Set.commandName -> configSetCommand.onExecute(interaction, response)
                CommandName.Config.Get.commandName -> configGetCommand.onExecute(interaction, response)
            }
        } ?: response.respond {
            val guildId = interaction.data.guildId.value
            val discordLocale = interaction.guildLocale
            content = localizationService.getString(LocalizationKeys.ERROR_UNEXPECTED, guildId = guildId, discordLocale = discordLocale)
        }
    }

    override suspend fun onAutoComplete(interaction: AutoCompleteInteraction) {
        val subCommandName = (interaction.command as? KordSubCommand)?.name
        when (subCommandName) {
            CommandName.Config.Get.commandName -> configGetCommand.onAutoComplete(interaction)
        }
    }
}
