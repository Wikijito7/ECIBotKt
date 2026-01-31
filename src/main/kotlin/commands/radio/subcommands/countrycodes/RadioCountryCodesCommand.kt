package es.wokis.commands.radio.subcommands.countrycodes

import dev.kord.core.behavior.interaction.response.DeferredPublicMessageInteractionResponseBehavior
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.entity.interaction.ChatInputCommandInteraction
import dev.kord.rest.builder.interaction.GlobalChatInputCreateBuilder
import dev.kord.rest.builder.interaction.subCommand
import es.wokis.commands.CommandName
import es.wokis.commands.SubCommand
import es.wokis.data.response.RemoteResponse
import es.wokis.localization.LocalizationKeys
import es.wokis.services.localization.LocalizationService
import es.wokis.services.radio.RadioService
import es.wokis.utils.orDefaultLocale

class RadioCountryCodesCommand(
    private val radioService: RadioService,
    private val localizationService: LocalizationService
) : SubCommand {

    override suspend fun onRegisterCommand(builder: GlobalChatInputCreateBuilder) {
        builder.apply {
            subCommand(CommandName.Radio.CountryCodes.commandName, "Get country codes")
        }
    }

    override suspend fun onExecute(
        interaction: ChatInputCommandInteraction,
        response: DeferredPublicMessageInteractionResponseBehavior
    ) {
        val locale = interaction.guildLocale.orDefaultLocale()

        when (val result = radioService.getCountryCodes()) {
            is RemoteResponse.Success -> {
                val countryCodes = result.data?.countryCodes.orEmpty()
                val formattedCodes = if (countryCodes.isNotEmpty()) {
                    countryCodes.joinToString(separator = ", ")
                } else {
                    localizationService.getString(
                        key = LocalizationKeys.RADIO_COUNTRYCODES_EMPTY,
                        locale = locale
                    )
                }

                response.respond {
                    content = localizationService.getStringFormat(
                        key = LocalizationKeys.RADIO_COUNTRYCODES_LIST,
                        locale = locale,
                        arguments = arrayOf(formattedCodes)
                    )
                }
            }
            is RemoteResponse.Error -> {
                response.respond {
                    content = localizationService.getString(
                        key = LocalizationKeys.RADIO_COUNTRYCODES_ERROR,
                        locale = locale
                    )
                }
            }
        }
    }
}
