package es.wokis.commands

import dev.kord.core.behavior.interaction.response.DeferredPublicMessageInteractionResponseBehavior
import dev.kord.core.entity.interaction.ChatInputCommandInteraction
import dev.kord.rest.builder.interaction.GlobalChatInputCreateBuilder

interface SubCommand {

    suspend fun onRegisterCommand(builder: GlobalChatInputCreateBuilder)

    suspend fun onExecute(interaction: ChatInputCommandInteraction, response: DeferredPublicMessageInteractionResponseBehavior)
}
