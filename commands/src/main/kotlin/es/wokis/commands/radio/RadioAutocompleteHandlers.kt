package es.wokis.commands.radio

import es.wokis.commands.neutral.AutocompleteContext
import es.wokis.commands.neutral.AutocompleteHandler
import es.wokis.commands.neutral.AutocompleteResponder

private const val MAX_SUGGESTIONS = 25
private const val MAX_CHOICE_LENGTH = 100

/**
 * Neutral `/radio play` autocomplete: name matches from the catalog, mirroring Kord's
 * `RadioPlayCommand.onAutoComplete`. Kord truncates each choice with `takeAtMost(100)`.
 */
class RadioPlayAutocompleteHandler(
    private val catalog: RadioCatalogPort,
) : AutocompleteHandler {

    override val commandName: String = RadioCommandDefinitions.RADIO_COMMAND_NAME

    override val optionName: String = RadioCommandDefinitions.PLAY_OPTION_NAME

    override suspend fun handle(context: AutocompleteContext, responder: AutocompleteResponder) {
        if (context.input.isEmpty()) {
            responder.respond(emptyList())
            return
        }
        responder.respond(
            catalog.searchStations(context.input)
                .take(MAX_SUGGESTIONS)
                .map { station -> station.name.takeAtMost(MAX_CHOICE_LENGTH) },
        )
    }
}

/**
 * Neutral `/radio search countrycode` autocomplete: country codes containing the typed text,
 * mirroring Kord's `RadioSearchCountryCodeCommand.onAutoComplete`.
 */
class RadioCountryCodeAutocompleteHandler(
    private val catalog: RadioCatalogPort,
) : AutocompleteHandler {

    override val commandName: String = RadioCommandDefinitions.RADIO_COMMAND_NAME

    override val optionName: String = RadioCommandDefinitions.SEARCH_COUNTRY_CODE_OPTION_NAME

    override suspend fun handle(context: AutocompleteContext, responder: AutocompleteResponder) {
        if (context.input.isEmpty()) {
            responder.respond(emptyList())
            return
        }
        responder.respond(
            catalog.countryCodes()
                .orEmpty()
                .filter { code -> code.contains(context.input, ignoreCase = true) }
                .take(MAX_SUGGESTIONS),
        )
    }
}
