package es.wokis.commands.radio

import dev.kord.core.Kord
import dev.kord.core.behavior.interaction.response.DeferredPublicMessageInteractionResponseBehavior
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.entity.interaction.ChatInputCommandInteraction
import dev.kord.core.entity.interaction.ComponentInteraction
import es.wokis.commands.CommandName
import es.wokis.commands.Component
import es.wokis.commands.GroupCommand
import es.wokis.commands.radio.subcommands.list.RadioListCommand
import es.wokis.commands.radio.subcommands.play.RadioPlayCommand
import es.wokis.commands.radio.subcommands.search.RadioSearchGroupCommand
import dev.kord.core.entity.interaction.SubCommand as KordSubCommand
import dev.kord.core.entity.interaction.GroupCommand as KordGroupCommand

class RadioGroupCommand(
    private val radioPlayCommand: RadioPlayCommand,
    private val radioListCommand: RadioListCommand,
    private val radioSearchGroupCommand: RadioSearchGroupCommand
) : GroupCommand, Component {

    override suspend fun onRegisterCommand(kord: Kord) {
        kord.createGlobalChatInputCommand("radio", "radio") {
            radioPlayCommand.onRegisterCommand(this)
            radioListCommand.onRegisterCommand(this)
            radioSearchGroupCommand.onRegisterCommand(this)
        }
    }

    override suspend fun onExecute(
        interaction: ChatInputCommandInteraction,
        response: DeferredPublicMessageInteractionResponseBehavior
    ) {
        val commandName = (interaction.command as? KordSubCommand)?.name ?: (interaction.command as? KordGroupCommand)?.groupName
        commandName?.let {
            when (commandName) {
                CommandName.Radio.Play.commandName -> radioPlayCommand.onExecute(interaction, response)
                CommandName.Radio.List.commandName -> radioListCommand.onExecute(interaction, response)
                CommandName.Radio.Search.commandName -> radioSearchGroupCommand.onExecute(interaction, response)
            }
        } ?: response.respond {
            content = "Unknown message"
        }
    }

    override suspend fun onInteract(interaction: ComponentInteraction) {
        TODO("Not yet implemented")
    }
}
