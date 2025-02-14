package es.wokis.commands

import dev.kord.core.behavior.interaction.response.DeferredPublicMessageInteractionResponseBehavior
import dev.kord.core.entity.interaction.ChatInputCommandInteraction
import dev.kord.rest.builder.interaction.GlobalMultiApplicationCommandBuilder

interface Command {

    fun onRegisterCommand(commandBuilder: GlobalMultiApplicationCommandBuilder)

    suspend fun onExecute(interaction: ChatInputCommandInteraction, response: DeferredPublicMessageInteractionResponseBehavior)
}