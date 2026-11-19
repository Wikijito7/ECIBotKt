package es.wokis.commands.radio

import es.wokis.commands.neutral.CommandDefinition
import es.wokis.commands.neutral.StringOptionDefinition
import es.wokis.commands.neutral.SubcommandDefinition
import es.wokis.commands.neutral.SubcommandGroupDefinition
import es.wokis.localization.GuildLocalizationService
import es.wokis.localization.RadioMessageKeys

/**
 * Gateway-neutral `/radio` definition, mirroring Kord's registration exactly.
 *
 * Kord publishes `play`, `list`, `search` (a subcommand group with `name` and `countrycode`),
 * `countrycodes` and `random`; option names, required flags and autocomplete flags are identical.
 */
object RadioCommandDefinitions {
    const val RADIO_COMMAND_NAME = "radio"

    const val PLAY_COMMAND_NAME = "play"
    const val LIST_COMMAND_NAME = "list"
    const val SEARCH_COMMAND_NAME = "search"
    const val SEARCH_NAME_COMMAND_NAME = "name"
    const val SEARCH_COUNTRY_CODE_COMMAND_NAME = "countrycode"
    const val RANDOM_COMMAND_NAME = "random"
    const val COUNTRY_CODES_COMMAND_NAME = "countrycodes"

    const val PLAY_OPTION_NAME = "radio"
    const val SEARCH_NAME_OPTION_NAME = "name"
    const val SEARCH_COUNTRY_CODE_OPTION_NAME = "countrycode"

    fun radio(localization: GuildLocalizationService): CommandDefinition = CommandDefinition(
        name = RADIO_COMMAND_NAME,
        description = localization.getDefaultString(RadioMessageKeys.RADIO_COMMAND_DESCRIPTION),
        adminOnly = false,
        descriptionLocalizations = localization.getLocalizations(RadioMessageKeys.RADIO_COMMAND_DESCRIPTION),
        subcommands = listOf(
            play(localization),
            list(localization),
            countryCodes(localization),
            random(localization),
        ),
        subcommandGroups = listOf(search(localization)),
    )

    private fun play(localization: GuildLocalizationService): SubcommandDefinition = SubcommandDefinition(
        name = PLAY_COMMAND_NAME,
        description = localization.getDefaultString(RadioMessageKeys.RADIO_PLAY_COMMAND_DESCRIPTION),
        descriptionLocalizations = localization.getLocalizations(RadioMessageKeys.RADIO_PLAY_COMMAND_DESCRIPTION),
        stringOption = StringOptionDefinition(
            name = PLAY_OPTION_NAME,
            description = localization.getDefaultString(RadioMessageKeys.RADIO_PLAY_INPUT_DESCRIPTION),
            required = true,
            autocomplete = true,
            descriptionLocalizations = localization.getLocalizations(
                RadioMessageKeys.RADIO_PLAY_INPUT_DESCRIPTION,
            ),
        ),
    )

    private fun list(localization: GuildLocalizationService): SubcommandDefinition = SubcommandDefinition(
        name = LIST_COMMAND_NAME,
        description = localization.getDefaultString(RadioMessageKeys.RADIO_LIST_COMMAND_DESCRIPTION),
        descriptionLocalizations = localization.getLocalizations(RadioMessageKeys.RADIO_LIST_COMMAND_DESCRIPTION),
    )

    private fun countryCodes(localization: GuildLocalizationService): SubcommandDefinition = SubcommandDefinition(
        name = COUNTRY_CODES_COMMAND_NAME,
        description = localization.getDefaultString(RadioMessageKeys.RADIO_COUNTRYCODES_COMMAND_DESCRIPTION),
        descriptionLocalizations = localization.getLocalizations(
            RadioMessageKeys.RADIO_COUNTRYCODES_COMMAND_DESCRIPTION,
        ),
    )

    private fun random(localization: GuildLocalizationService): SubcommandDefinition = SubcommandDefinition(
        name = RANDOM_COMMAND_NAME,
        description = localization.getDefaultString(RadioMessageKeys.RADIO_RANDOM_COMMAND_DESCRIPTION),
        descriptionLocalizations = localization.getLocalizations(RadioMessageKeys.RADIO_RANDOM_COMMAND_DESCRIPTION),
    )

    private fun search(localization: GuildLocalizationService): SubcommandGroupDefinition = SubcommandGroupDefinition(
        name = SEARCH_COMMAND_NAME,
        description = localization.getDefaultString(RadioMessageKeys.RADIO_SEARCH_COMMAND_DESCRIPTION),
        descriptionLocalizations = localization.getLocalizations(RadioMessageKeys.RADIO_SEARCH_COMMAND_DESCRIPTION),
        subcommands = listOf(
            searchByName(localization),
            searchByCountryCode(localization),
        ),
    )

    private fun searchByName(localization: GuildLocalizationService): SubcommandDefinition = SubcommandDefinition(
        name = SEARCH_NAME_COMMAND_NAME,
        description = localization.getDefaultString(RadioMessageKeys.RADIO_SEARCH_NAME_COMMAND_DESCRIPTION),
        descriptionLocalizations = localization.getLocalizations(
            RadioMessageKeys.RADIO_SEARCH_NAME_COMMAND_DESCRIPTION,
        ),
        stringOption = StringOptionDefinition(
            name = SEARCH_NAME_OPTION_NAME,
            description = localization.getDefaultString(RadioMessageKeys.RADIO_SEARCH_NAME_INPUT_DESCRIPTION),
            required = true,
            descriptionLocalizations = localization.getLocalizations(
                RadioMessageKeys.RADIO_SEARCH_NAME_INPUT_DESCRIPTION,
            ),
        ),
    )

    private fun searchByCountryCode(localization: GuildLocalizationService): SubcommandDefinition =
        SubcommandDefinition(
            name = SEARCH_COUNTRY_CODE_COMMAND_NAME,
            description = localization.getDefaultString(
                RadioMessageKeys.RADIO_SEARCH_COUNTRYCODE_COMMAND_DESCRIPTION,
            ),
            descriptionLocalizations = localization.getLocalizations(
                RadioMessageKeys.RADIO_SEARCH_COUNTRYCODE_COMMAND_DESCRIPTION,
            ),
            stringOption = StringOptionDefinition(
                name = SEARCH_COUNTRY_CODE_OPTION_NAME,
                description = localization.getDefaultString(
                    RadioMessageKeys.RADIO_SEARCH_COUNTRYCODE_INPUT_DESCRIPTION,
                ),
                required = true,
                autocomplete = true,
                descriptionLocalizations = localization.getLocalizations(
                    RadioMessageKeys.RADIO_SEARCH_COUNTRYCODE_INPUT_DESCRIPTION,
                ),
            ),
        )
}
