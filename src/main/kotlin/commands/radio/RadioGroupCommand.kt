package es.wokis.commands.radio

import dev.kord.core.Kord
import dev.kord.core.behavior.interaction.response.DeferredPublicMessageInteractionResponseBehavior
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.entity.interaction.ChatInputCommandInteraction
import es.wokis.commands.CommandName
import es.wokis.commands.GroupCommand
import es.wokis.commands.radio.subcommands.RadioPlayCommand
import dev.kord.core.entity.interaction.GroupCommand as KordGroupCommand


class RadioGroupCommand(
    private val radioPlayCommand: RadioPlayCommand
) : GroupCommand {

    override suspend fun onRegisterCommand(kord: Kord) {
        kord.createGlobalChatInputCommand("radio", "radio") {
            radioPlayCommand.onRegisterCommand(this)
        }
    }

    override suspend fun onExecute(
        interaction: ChatInputCommandInteraction,
        response: DeferredPublicMessageInteractionResponseBehavior
    ) {
        val command = interaction.command as? KordGroupCommand
        command?.let {
            when (command.name) {
                CommandName.Radio.Play.commandName -> radioPlayCommand.onExecute(interaction, response)
                CommandName.Radio.List.commandName -> radioPlayCommand.onExecute(interaction, response)
                CommandName.Radio.Search.commandName -> radioPlayCommand.onExecute(interaction, response)
            }
        } ?: response.respond {
            content = "Unknown message"
        }
    }
}
