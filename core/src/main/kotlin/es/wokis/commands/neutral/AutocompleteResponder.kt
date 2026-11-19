package es.wokis.commands.neutral

/** Gateway-neutral autocomplete reply: at most 25 suggestions, each used as both label and value. */
interface AutocompleteResponder {

    suspend fun respond(suggestions: List<String>)
}
