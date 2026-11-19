package es.wokis.jda.command

import es.wokis.commands.neutral.AutocompleteContext
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent

/** Maps a JDA autocomplete event to the neutral context; the focused option supplies the input. */
class JdaAutocompleteContextMapper {

    fun map(event: CommandAutoCompleteInteractionEvent): AutocompleteContext = AutocompleteContext(
        commandName = event.name,
        optionName = event.focusedOption.name,
        guildId = event.guild?.idLong?.toGuildId(),
        input = event.focusedOption.value,
    )
}
