package es.wokis.commands.radio.subcommands.search

import dev.kord.core.behavior.interaction.response.DeferredPublicMessageInteractionResponseBehavior
import dev.kord.core.entity.component.ButtonComponent
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

class RadioSearchCountryCodeCommand(
    private val radioService: RadioService,
    private val localizationService: LocalizationService
) : SubCommand, Component {

    override suspend fun onRegisterCommand(builder: GlobalChatInputCreateBuilder) = Unit

    override suspend fun onExecute(
        interaction: ChatInputCommandInteraction,
        response: DeferredPublicMessageInteractionResponseBehavior
    ) {
        val countryCode: String = interaction.command.strings["countrycode"].orEmpty()
        val currentRadioPage = radioService.searchRadioByCountryCodePaged(countryCode, 1).data
        onExecuteRadioListCommand(
            interaction = interaction,
            currentRadioPage = currentRadioPage,
            response = response,
            localizationService = localizationService,
            previousButtonCustomId = getPreviousCustomId(countryCode),
            nextButtonCustomId = getNextCustomId(countryCode)
        )
    }

    override suspend fun onInteract(interaction: ComponentInteraction) {
        val countryCode: String = (interaction as? ButtonInteraction)?.component?.customId
            ?.split(CUSTOM_COMPONENT_SEPARATOR)?.lastOrNull() ?: return
        onInteractRadioListCommand(
            interaction = interaction,
            localizationService = localizationService,
            previousButtonCustomId = getPreviousCustomId(countryCode),
            nextButtonCustomId = getNextCustomId(countryCode),
            block = {
                radioService.searchRadioByCountryCodePaged(countryCode, it).data
            }
        )
    }

    private fun getNextCustomId(countryCode: String) =
        ComponentsEnum.RADIO_SEARCH_COUNTRY_CODE_NEXT.customId.plus("$CUSTOM_COMPONENT_SEPARATOR$countryCode")

    private fun getPreviousCustomId(countryCode: String) =
        ComponentsEnum.RADIO_SEARCH_COUNTRY_CODE_PREVIOUS.customId.plus("$CUSTOM_COMPONENT_SEPARATOR$countryCode")
}
