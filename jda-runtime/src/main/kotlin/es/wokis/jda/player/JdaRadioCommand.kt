package es.wokis.jda.player

import es.wokis.commands.radio.RadioCommandDefinitions
import es.wokis.commands.radio.RadioCommandHandler
import es.wokis.localization.GuildLocalizationService
import es.wokis.message.ComponentId

/**
 * `/radio`; Kord parity: a group command with four subcommands (play, list, countrycodes, random)
 * and one search subcommand group (name, countrycode).
 *
 * The adapter only publishes the definition and defers the public reply; [RadioCommandHandler]
 * routes the subcommand segments the context mapper carries.
 */
internal class JdaRadioCommand(
    handler: RadioCommandHandler,
    localizationService: GuildLocalizationService,
) : JdaNeutralPlayerCommand(handler, RadioCommandDefinitions.radio(localizationService))

/** Component-id pairs the radio component router must handle. */
internal val RADIO_LIST_PAGINATION_IDS: List<ComponentId> = listOf(
    ComponentId.RADIO_LIST_PREVIOUS,
    ComponentId.RADIO_LIST_NEXT,
)

internal val RADIO_SEARCH_NAME_PAGINATION_IDS: List<ComponentId> = listOf(
    ComponentId.RADIO_SEARCH_NAME_PREVIOUS,
    ComponentId.RADIO_SEARCH_NAME_NEXT,
)

internal val RADIO_SEARCH_COUNTRY_CODE_PAGINATION_IDS: List<ComponentId> = listOf(
    ComponentId.RADIO_SEARCH_COUNTRY_CODE_PREVIOUS,
    ComponentId.RADIO_SEARCH_COUNTRY_CODE_NEXT,
)

internal val RADIO_COUNTRY_CODES_PAGINATION_IDS: List<ComponentId> = listOf(
    ComponentId.RADIO_COUNTRYCODES_PREVIOUS,
    ComponentId.RADIO_COUNTRYCODES_NEXT,
)
