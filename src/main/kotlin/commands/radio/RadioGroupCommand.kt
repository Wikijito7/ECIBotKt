package es.wokis.commands.radio

import dev.kord.core.Kord
import dev.kord.core.behavior.interaction.response.DeferredPublicMessageInteractionResponseBehavior
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.entity.interaction.AutoCompleteInteraction
import dev.kord.core.entity.interaction.ButtonInteraction
import dev.kord.core.entity.interaction.ChatInputCommandInteraction
import dev.kord.core.entity.interaction.ComponentInteraction
import es.wokis.commands.*
import es.wokis.commands.radio.subcommands.countrycodes.RadioCountryCodesCommand
import es.wokis.commands.radio.subcommands.list.RadioListCommand
import es.wokis.commands.radio.subcommands.play.RadioPlayCommand
import es.wokis.commands.radio.subcommands.random.RadioRandomCommand
import es.wokis.commands.radio.subcommands.search.RadioSearchGroupCommand
import es.wokis.constants.CUSTOM_COMPONENT_SEPARATOR
import dev.kord.core.entity.interaction.SubCommand as KordSubCommand
import dev.kord.core.entity.interaction.GroupCommand as KordGroupCommand

class RadioGroupCommand(
    private val radioPlayCommand: RadioPlayCommand,
    private val radioListCommand: RadioListCommand,
    private val radioSearchGroupCommand: RadioSearchGroupCommand,
    private val radioCountryCodesCommand: RadioCountryCodesCommand,
    private val radioRandomCommand: RadioRandomCommand
) : GroupCommand, Component, Autocomplete {

    override suspend fun onRegisterCommand(kord: Kord) {
        kord.createGlobalChatInputCommand(CommandName.Radio.commandName, "Base radio command") {
            radioPlayCommand.onRegisterCommand(this)
            radioListCommand.onRegisterCommand(this)
            radioSearchGroupCommand.onRegisterCommand(this)
            radioCountryCodesCommand.onRegisterCommand(this)
            radioRandomCommand.onRegisterCommand(this)
        }
    }

    override suspend fun onExecute(
        interaction: ChatInputCommandInteraction,
        response: DeferredPublicMessageInteractionResponseBehavior
    ) {
        val commandName = (interaction.command as? KordSubCommand)?.name ?: (interaction.command as? KordGroupCommand)?.groupName
        commandName?.let {
            when (commandName) {
                CommandName.Radio.Play.commandName -> radioPlayCommand.onExecute(interaction, response)
                CommandName.Radio.List.commandName -> radioListCommand.onExecute(interaction, response)
                CommandName.Radio.Search.commandName -> radioSearchGroupCommand.onExecute(interaction, response)
                CommandName.Radio.CountryCodes.commandName -> radioCountryCodesCommand.onExecute(interaction, response)
                CommandName.Radio.Random.commandName -> radioRandomCommand.onExecute(interaction, response)
            }
        } ?: response.respond {
            content = "Unknown message"
        }
    }

    override suspend fun onInteract(interaction: ComponentInteraction) {
        val customId = (interaction as? ButtonInteraction)?.component?.customId?.split(CUSTOM_COMPONENT_SEPARATOR)?.firstOrNull()

        when (customId) {
            ComponentsEnum.RADIO_LIST_NEXT.customId, ComponentsEnum.RADIO_LIST_PREVIOUS.customId -> radioListCommand.onInteract(interaction)

            ComponentsEnum.RADIO_SEARCH_COUNTRY_CODE_NEXT.customId,
            ComponentsEnum.RADIO_SEARCH_COUNTRY_CODE_PREVIOUS.customId,
            ComponentsEnum.RADIO_SEARCH_NAME_PREVIOUS.customId,
            ComponentsEnum.RADIO_SEARCH_COUNTRY_CODE_NEXT.customId -> radioSearchGroupCommand.onInteract(interaction)
        }
    }

    override suspend fun onAutoComplete(interaction: AutoCompleteInteraction) {
        val commandName = (interaction.command as? KordSubCommand)?.name ?: (interaction.command as? KordGroupCommand)?.groupName
        when (commandName) {
            CommandName.Radio.Play.commandName -> radioPlayCommand.onAutoComplete(interaction)
            CommandName.Radio.Search.CountryCode.commandName -> radioSearchGroupCommand.onAutoComplete(interaction)
        }
    }
}
