package es.wokis.commands

import dev.kord.core.Kord
import dev.kord.core.behavior.interaction.response.DeferredPublicMessageInteractionResponseBehavior
import dev.kord.core.entity.interaction.ChatInputCommandInteraction
import dev.kord.rest.builder.interaction.GlobalChatInputCreateBuilder
import dev.kord.rest.builder.interaction.GlobalMultiApplicationCommandBuilder

interface GroupCommand {

    suspend fun onRegisterCommand(kord: Kord)

    suspend fun onExecute(interaction: ChatInputCommandInteraction, response: DeferredPublicMessageInteractionResponseBehavior)
}
