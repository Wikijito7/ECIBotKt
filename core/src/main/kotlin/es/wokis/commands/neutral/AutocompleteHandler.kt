package es.wokis.commands.neutral

/** Gateway-neutral autocomplete handler for one command option. */
interface AutocompleteHandler {

    val commandName: String

    val optionName: String

    suspend fun handle(context: AutocompleteContext, responder: AutocompleteResponder)
}
