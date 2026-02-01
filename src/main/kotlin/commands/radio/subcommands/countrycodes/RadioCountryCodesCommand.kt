package es.wokis.commands.radio.subcommands.countrycodes

import dev.kord.common.Color
import dev.kord.core.behavior.interaction.response.DeferredPublicMessageInteractionResponseBehavior
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.entity.interaction.ChatInputCommandInteraction
import dev.kord.rest.builder.interaction.GlobalChatInputCreateBuilder
import dev.kord.rest.builder.interaction.subCommand
import dev.kord.rest.builder.message.EmbedBuilder
import dev.kord.rest.builder.message.embed
import es.wokis.commands.CommandName
import es.wokis.commands.SubCommand
import es.wokis.constants.BLANK_SPACE
import es.wokis.data.response.RemoteResponse
import es.wokis.localization.LocalizationKeys
import es.wokis.services.localization.LocalizationService
import es.wokis.services.radio.RadioService
import es.wokis.utils.orDefaultLocale

private const val MAX_FIELD_LENGTH = 1000 // Stay under 1024 limit
private const val MAX_FIELDS = 25 // Discord embed limit
private const val ITEMS_PER_LINE = 8

private fun getFlagEmoji(countryCode: String): String = if (countryCode == "UNK") {
    "❓"
} else {
    countryCode.uppercase().map { char ->
        Character.toChars(0x1F1E6 + (char - 'A')).joinToString("")
    }.joinToString("")
}

private fun groupCountryCodesToFields(countryCodes: List<String>): List<String> {
    if (countryCodes.isEmpty()) return emptyList()

    val formattedCodes = countryCodes.map { "${getFlagEmoji(it)} $it" }
    val fields = mutableListOf<String>()
    var currentField = StringBuilder()

    formattedCodes.forEachIndexed { index, code ->
        val isLastItem = index == formattedCodes.size - 1
        val separator = if ((index + 1) % ITEMS_PER_LINE == 0 || isLastItem) "" else BLANK_SPACE
        val nextLength = currentField.length + code.length + separator.length +
            if ((index + 1) % ITEMS_PER_LINE == 0) 1 else 0 // Account for newline

        // Check if we need to start a new field
        if (nextLength > MAX_FIELD_LENGTH && currentField.isNotEmpty()) {
            fields.add(currentField.toString())
            currentField = StringBuilder()
            if (fields.size >= MAX_FIELDS) {
                return fields // Stop at max fields
            }
        }

        currentField.append(code)
        if (!isLastItem) {
            if ((index + 1) % ITEMS_PER_LINE == 0) {
                currentField.append("\n")
            } else {
                currentField.append(separator)
            }
        }
    }

    // Add remaining content
    if (currentField.isNotEmpty() && fields.size < MAX_FIELDS) {
        fields.add(currentField.toString())
    }

    return fields
}

class RadioCountryCodesCommand(
    private val radioService: RadioService,
    private val localizationService: LocalizationService
) : SubCommand {

    override suspend fun onRegisterCommand(builder: GlobalChatInputCreateBuilder) {
        builder.apply {
            subCommand(CommandName.Radio.CountryCodes.commandName, localizationService.getString(LocalizationKeys.RADIO_COUNTRYCODES_COMMAND_DESCRIPTION)) {
                descriptionLocalizations = localizationService.getLocalizations(LocalizationKeys.RADIO_COUNTRYCODES_COMMAND_DESCRIPTION)
            }
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

                if (countryCodes.isEmpty()) {
                    response.respond {
                        content = localizationService.getString(
                            key = LocalizationKeys.RADIO_COUNTRYCODES_EMPTY,
                            locale = locale
                        )
                    }
                    return
                }

                val fieldGroups = groupCountryCodesToFields(countryCodes)

                response.respond {
                    embed {
                        title = localizationService.getString(
                            key = LocalizationKeys.RADIO_COUNTRYCODES_EMBED_TITLE,
                            locale = locale
                        )
                        color = Color(0x01B05B)

                        fieldGroups.forEach { fieldContent ->
                            field {
                                name = BLANK_SPACE
                                value = fieldContent
                                inline = false
                            }
                        }
                    }
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
