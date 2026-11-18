package es.wokis.commands.sound

import es.wokis.commands.neutral.AutocompleteContext
import es.wokis.commands.neutral.AutocompleteHandler
import es.wokis.commands.neutral.AutocompleteResponder
import es.wokis.commands.player.PlayerCommandDefinitions

private const val MAX_SUGGESTIONS = 25
private const val MAX_CHOICE_LENGTH = 100

/** Neutral `/sound` autocomplete: prefix matches first, then substring matches, mirroring Kord. */
class SoundAutocompleteHandler(
    private val soundsCatalog: SoundsCatalogPort,
) : AutocompleteHandler {

    override val commandName: String = PlayerCommandDefinitions.SOUND_COMMAND_NAME

    override val optionName: String = PlayerCommandDefinitions.SOUND_OPTION_NAME

    override suspend fun handle(context: AutocompleteContext, responder: AutocompleteResponder) {
        if (context.input.isEmpty()) {
            responder.respond(emptyList())
            return
        }
        val names = soundsCatalog.soundNames()
        val prefixMatches = names
            .filter { name -> name.startsWith(context.input, ignoreCase = true) }
            .sortedBy { name -> name.lowercase() }
        val substringMatches = names
            .filter { name ->
                !name.startsWith(context.input, ignoreCase = true) &&
                    name.contains(context.input, ignoreCase = true)
            }
            .sortedBy { name -> name.lowercase() }
        responder.respond(
            (prefixMatches + substringMatches)
                .take(MAX_SUGGESTIONS)
                .map { name -> name.take(MAX_CHOICE_LENGTH) },
        )
    }
}
