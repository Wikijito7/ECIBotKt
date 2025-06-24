package es.wokis.commands.radio.subcommands.search

import dev.kord.core.behavior.interaction.response.DeferredPublicMessageInteractionResponseBehavior
import dev.kord.core.entity.interaction.ButtonInteraction
import dev.kord.core.entity.interaction.ChatInputCommandInteraction
import dev.kord.core.entity.interaction.ComponentInteraction
import dev.kord.rest.builder.interaction.GlobalChatInputCreateBuilder
import es.wokis.commands.Component
import es.wokis.commands.ComponentsEnum
import es.wokis.commands.SubCommand
import es.wokis.commands.radio.onExecuteRadioListCommand
import es.wokis.commands.radio.onInteractRadioListCommand
import es.wokis.constants.CUSTOM_COMPONENT_SEPARATOR
import es.wokis.services.localization.LocalizationService
import es.wokis.services.radio.RadioService

class RadioSearchNameCommand(
    private val radioService: RadioService,
    private val localizationService: LocalizationService
) : SubCommand, Component {

    override suspend fun onRegisterCommand(builder: GlobalChatInputCreateBuilder) = Unit

    override suspend fun onExecute(
        interaction: ChatInputCommandInteraction,
        response: DeferredPublicMessageInteractionResponseBehavior
    ) {
        val name: String = interaction.command.strings["name"].orEmpty()
        val currentRadioPage = radioService.searchRadioByNamePaged(name, 1).data
        onExecuteRadioListCommand(
            interaction = interaction,
            currentRadioPage = currentRadioPage,
            response = response,
            localizationService = localizationService,
            previousButtonCustomId = getPreviousCustomId(name),
            nextButtonCustomId = getNextCustomId(name)
        )
    }

    override suspend fun onInteract(interaction: ComponentInteraction) {
        val name: String = (interaction as? ButtonInteraction)?.component?.customId
            ?.split(CUSTOM_COMPONENT_SEPARATOR)?.lastOrNull() ?: return
        onInteractRadioListCommand(
            interaction = interaction,
            localizationService = localizationService,
            previousButtonCustomId = getPreviousCustomId(name),
            nextButtonCustomId = getNextCustomId(name),
            block = {
                radioService.searchRadioByNamePaged(name, it).data
            }
        )
    }

    private fun getNextCustomId(name: String) =
        ComponentsEnum.RADIO_SEARCH_NAME_NEXT.customId.plus("$CUSTOM_COMPONENT_SEPARATOR$name")

    private fun getPreviousCustomId(name: String) =
        ComponentsEnum.RADIO_SEARCH_NAME_PREVIOUS.customId.plus("$CUSTOM_COMPONENT_SEPARATOR$name")
}
