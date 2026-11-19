package es.wokis.commands.locale

import es.wokis.commands.neutral.AutocompleteContext
import es.wokis.commands.neutral.AutocompleteHandler
import es.wokis.commands.neutral.AutocompleteResponder
import es.wokis.domain.locale.DiscordLocaleCatalog

/** Neutral `/locale` autocomplete over the canonical tag catalog. */
class LocaleAutocompleteHandler : AutocompleteHandler {

    override val commandName: String = LocaleCommandHandler.COMMAND_NAME

    override val optionName: String = LocaleCommandHandler.OPTION_NAME

    override suspend fun handle(context: AutocompleteContext, responder: AutocompleteResponder) {
        responder.respond(DiscordLocaleCatalog.suggestions(context.input).map { it.value })
    }
}
