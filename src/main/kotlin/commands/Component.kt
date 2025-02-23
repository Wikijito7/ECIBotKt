package es.wokis.commands

import dev.kord.core.entity.interaction.ComponentInteraction

interface Component {
    suspend fun onInteract(interaction: ComponentInteraction)
}
