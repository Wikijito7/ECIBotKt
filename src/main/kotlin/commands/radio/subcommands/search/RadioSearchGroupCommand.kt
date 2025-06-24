package es.wokis.commands.radio.subcommands.search

import dev.kord.core.behavior.interaction.response.DeferredPublicMessageInteractionResponseBehavior
import dev.kord.core.entity.interaction.*
import dev.kord.core.entity.interaction.GroupCommand
import dev.kord.rest.builder.interaction.GlobalChatInputCreateBuilder
import dev.kord.rest.builder.interaction.group
import dev.kord.rest.builder.interaction.string
import es.wokis.commands.*
import es.wokis.commands.SubCommand
import es.wokis.constants.CUSTOM_COMPONENT_SEPARATOR

class RadioSearchGroupCommand(
    private val radioSearchNameCommand: RadioSearchNameCommand,
    private val radioSearchCountryCodeCommand: RadioSearchCountryCodeCommand
) : SubCommand, Component, Autocomplete {

    override suspend fun onRegisterCommand(builder: GlobalChatInputCreateBuilder) {
        builder.apply {
            group(CommandName.Radio.Search.commandName, "aasdsa") {
                subCommand(CommandName.Radio.Search.Name.commandName, "asdasd") {
                    string("name", "radio name") {
                        required = true
                    }
                }
                subCommand(CommandName.Radio.Search.CountryCode.commandName, "sese") {
                    string("countrycode", "radio country code") {
                        required = true
                        autocomplete = true
                    }
                }
            }
        }
    }

    override suspend fun onExecute(
        interaction: ChatInputCommandInteraction,
        response: DeferredPublicMessageInteractionResponseBehavior
    ) {
        (interaction.command as? GroupCommand)?.name?.let { commandName ->
            when (commandName) {
                CommandName.Radio.Search.Name.commandName -> radioSearchNameCommand.onExecute(interaction, response)
                CommandName.Radio.Search.CountryCode.commandName -> radioSearchCountryCodeCommand.onExecute(interaction, response)
            }
        }
    }

    override suspend fun onInteract(interaction: ComponentInteraction) {
        val customId = (interaction as? ButtonInteraction)?.component?.customId?.split(CUSTOM_COMPONENT_SEPARATOR)?.firstOrNull()

        when (customId) {
            ComponentsEnum.RADIO_SEARCH_COUNTRY_CODE_NEXT.customId,
            ComponentsEnum.RADIO_SEARCH_COUNTRY_CODE_PREVIOUS.customId -> radioSearchCountryCodeCommand.onInteract(interaction)

            ComponentsEnum.RADIO_SEARCH_NAME_PREVIOUS.customId,
            ComponentsEnum.RADIO_SEARCH_COUNTRY_CODE_NEXT.customId -> radioSearchNameCommand.onInteract(interaction)
        }
    }

    override suspend fun onAutoComplete(interaction: AutoCompleteInteraction) {

    }
}
