package es.wokis.commands.radio.subcommands.search

import dev.kord.common.entity.Choice
import dev.kord.common.entity.optional.Optional
import dev.kord.core.behavior.interaction.response.DeferredPublicMessageInteractionResponseBehavior
import dev.kord.core.behavior.interaction.suggest
import dev.kord.core.entity.component.ButtonComponent
import dev.kord.core.entity.interaction.AutoCompleteInteraction
import dev.kord.core.entity.interaction.ButtonInteraction
import dev.kord.core.entity.interaction.ChatInputCommandInteraction
import dev.kord.core.entity.interaction.ComponentInteraction
import dev.kord.rest.builder.interaction.GlobalChatInputCreateBuilder
import es.wokis.commands.Autocomplete
import es.wokis.commands.Component
import es.wokis.commands.ComponentsEnum
import es.wokis.commands.SubCommand
import es.wokis.commands.radio.onExecuteRadioListCommand
import es.wokis.commands.radio.onInteractRadioListCommand
import es.wokis.constants.CUSTOM_COMPONENT_SEPARATOR
import es.wokis.data.response.RemoteResponse
import es.wokis.services.localization.LocalizationService
import es.wokis.services.radio.RadioService
import es.wokis.utils.takeAtMost
import es.wokis.utils.takeIfNotEmpty

class RadioSearchCountryCodeCommand(
    private val radioService: RadioService,
    private val localizationService: LocalizationService
) : SubCommand, Component, Autocomplete {

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

    override suspend fun onAutoComplete(interaction: AutoCompleteInteraction) {
        val input = interaction.command.strings["countrycode"].orEmpty()
        input.takeIfNotEmpty()?.let {
            val countryCodes = (radioService.getCountryCodes() as? RemoteResponse.Success)?.data?.countryCodes.orEmpty()
            val filteredCodes = countryCodes.filter { code ->
                code.contains(input, ignoreCase = true)
            }.take(25)
            val choices = filteredCodes.map { code ->
                Choice.StringChoice(code, Optional.Missing(), code)
            }
            interaction.suggest(choices)
        } ?: interaction.suggest(emptyList())
    }
}
