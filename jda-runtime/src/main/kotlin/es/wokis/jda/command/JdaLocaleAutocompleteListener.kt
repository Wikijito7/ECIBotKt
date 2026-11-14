package es.wokis.jda.command

import es.wokis.commands.locale.LocaleCommandHandler
import es.wokis.domain.locale.DiscordLocaleCatalog
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.interactions.commands.Command

/**
 * Minimal autocomplete listener for the guild-scoped `/locale` command.
 *
 * Autocomplete is a gateway-interaction concern, so it stays in the JDA adapter. Suggestions are
 * canonical catalog tags; the option value is the same canonical tag that the command persists.
 */
internal class JdaLocaleAutocompleteListener : ListenerAdapter() {

    override fun onCommandAutoCompleteInteraction(event: CommandAutoCompleteInteractionEvent) {
        if (event.name != LocaleCommandHandler.COMMAND_NAME) return
        if (event.focusedOption.name != LocaleCommandHandler.OPTION_NAME) {
            event.replyChoices(emptyList()).queue()
            return
        }

        val choices = DiscordLocaleCatalog.suggestions(event.focusedOption.value)
            .map { Command.Choice(it.value, it.value) }
        event.replyChoices(choices).queue()
    }
}
