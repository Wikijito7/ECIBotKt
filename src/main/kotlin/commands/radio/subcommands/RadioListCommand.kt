package es.wokis.commands.radio.subcommands

import dev.kord.core.behavior.interaction.response.DeferredPublicMessageInteractionResponseBehavior
import dev.kord.core.entity.interaction.ChatInputCommandInteraction
import dev.kord.core.entity.interaction.ComponentInteraction
import dev.kord.rest.builder.interaction.GlobalChatInputCreateBuilder
import es.wokis.commands.Component
import es.wokis.commands.SubCommand

class RadioListCommand : SubCommand, Component {

    override suspend fun onRegisterCommand(builder: GlobalChatInputCreateBuilder) {
        TODO("Not yet implemented")
    }

    override suspend fun onExecute(
        interaction: ChatInputCommandInteraction,
        response: DeferredPublicMessageInteractionResponseBehavior
    ) {
        TODO("Not yet implemented")
    }

    override suspend fun onInteract(interaction: ComponentInteraction) {
        TODO("Not yet implemented")
    }
}
