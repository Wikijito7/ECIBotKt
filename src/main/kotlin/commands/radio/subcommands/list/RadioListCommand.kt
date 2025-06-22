package es.wokis.commands.radio.subcommands.list

import dev.kord.core.behavior.interaction.response.DeferredPublicMessageInteractionResponseBehavior
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.entity.interaction.ChatInputCommandInteraction
import dev.kord.core.entity.interaction.ComponentInteraction
import dev.kord.rest.builder.interaction.GlobalChatInputCreateBuilder
import dev.kord.rest.builder.interaction.subCommand
import es.wokis.commands.CommandName
import es.wokis.commands.Component
import es.wokis.commands.SubCommand

class RadioListCommand : SubCommand, Component {

    override suspend fun onRegisterCommand(builder: GlobalChatInputCreateBuilder) {
        builder.apply {
            subCommand(CommandName.Radio.List.commandName, "asd")
        }
    }

    override suspend fun onExecute(
        interaction: ChatInputCommandInteraction,
        response: DeferredPublicMessageInteractionResponseBehavior
    ) {
        response.respond {
            content = "list blablablas"
        }
    }

    override suspend fun onInteract(interaction: ComponentInteraction) {
        TODO("Not yet implemented")
    }
}
