package es.wokis.commands.radio

import es.wokis.message.ComponentId

/**
 * The two `/radio search` leaves. Each owns its option name and pagination id pair, mirroring
 * Kord's separate `RadioSearchNameCommand` and `RadioSearchCountryCodeCommand` registrations.
 */
enum class RadioSearchKind(
    val optionName: String,
    val previousButton: ComponentId,
    val nextButton: ComponentId,
) {
    NAME(
        optionName = RadioCommandDefinitions.SEARCH_NAME_OPTION_NAME,
        previousButton = ComponentId.RADIO_SEARCH_NAME_PREVIOUS,
        nextButton = ComponentId.RADIO_SEARCH_NAME_NEXT,
    ),
    COUNTRY_CODE(
        optionName = RadioCommandDefinitions.SEARCH_COUNTRY_CODE_OPTION_NAME,
        previousButton = ComponentId.RADIO_SEARCH_COUNTRY_CODE_PREVIOUS,
        nextButton = ComponentId.RADIO_SEARCH_COUNTRY_CODE_NEXT,
    ),
}

/** Runs [query]'s page lookup against the matching catalog search. */
internal suspend fun RadioCatalogPort.search(kind: RadioSearchKind, query: String, page: Int): RadioPage? =
    when (kind) {
        RadioSearchKind.NAME -> searchByName(query, page)
        RadioSearchKind.COUNTRY_CODE -> searchByCountryCode(query, page)
    }
