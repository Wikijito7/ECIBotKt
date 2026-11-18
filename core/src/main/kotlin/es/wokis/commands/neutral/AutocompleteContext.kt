package es.wokis.commands.neutral

import es.wokis.domain.model.GuildId

/** Gateway-neutral autocomplete interaction; [input] is the text typed into the focused option. */
data class AutocompleteContext(
    val commandName: String,
    val optionName: String,
    val guildId: GuildId?,
    val input: String,
)
