package es.wokis.commands

import dev.kord.core.entity.interaction.AutoCompleteInteraction

interface Autocomplete {
    suspend fun onAutoComplete(interaction: AutoCompleteInteraction)
}
