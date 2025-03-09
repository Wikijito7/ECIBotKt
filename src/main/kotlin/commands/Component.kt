package es.wokis.commands

import dev.kord.core.entity.interaction.ComponentInteraction

fun interface Component {
    suspend fun onInteract(interaction: ComponentInteraction)
}
