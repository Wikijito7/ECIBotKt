package es.wokis.commands.radio.subcommands.list

import dev.kord.core.behavior.interaction.response.DeferredPublicMessageInteractionResponseBehavior
import dev.kord.core.entity.interaction.ChatInputCommandInteraction
import dev.kord.core.entity.interaction.ComponentInteraction
import dev.kord.rest.builder.interaction.GlobalChatInputCreateBuilder
import dev.kord.rest.builder.interaction.subCommand
import es.wokis.commands.CommandName
import es.wokis.commands.Component
import es.wokis.commands.ComponentsEnum
import es.wokis.commands.SubCommand
import es.wokis.localization.LocalizationKeys
import es.wokis.commands.radio.onExecuteRadioListCommand
import es.wokis.commands.radio.onInteractRadioListCommand
import es.wokis.services.localization.LocalizationService
import es.wokis.services.radio.RadioService

class RadioListCommand(
    private val radioService: RadioService,
    private val localizationService: LocalizationService
) : SubCommand, Component {

    override suspend fun onRegisterCommand(builder: GlobalChatInputCreateBuilder) {
        builder.apply {
            subCommand(CommandName.Radio.List.commandName, localizationService.getString(LocalizationKeys.RADIO_LIST_COMMAND_DESCRIPTION)) {
                descriptionLocalizations = localizationService.getLocalizations(LocalizationKeys.RADIO_LIST_COMMAND_DESCRIPTION)
            }
        }
    }

    override suspend fun onExecute(
        interaction: ChatInputCommandInteraction,
        response: DeferredPublicMessageInteractionResponseBehavior
    ) {
        val currentRadioPage = radioService.getRadioList(1).data
        onExecuteRadioListCommand(
            interaction = interaction,
            currentRadioPage = currentRadioPage,
            response = response,
            previousButtonCustomId = ComponentsEnum.RADIO_LIST_PREVIOUS.customId,
            nextButtonCustomId = ComponentsEnum.RADIO_LIST_NEXT.customId,
            localizationService = localizationService
        )
    }

    override suspend fun onInteract(interaction: ComponentInteraction) {
        onInteractRadioListCommand(
            interaction = interaction,
            localizationService = localizationService,
            previousButtonCustomId = ComponentsEnum.RADIO_LIST_PREVIOUS.customId,
            nextButtonCustomId = ComponentsEnum.RADIO_LIST_NEXT.customId,
            block = {
                radioService.getRadioList(it).data
            }
        )
    }
}
