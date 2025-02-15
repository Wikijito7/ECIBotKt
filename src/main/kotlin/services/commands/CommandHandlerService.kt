package es.wokis.services.commands

import dev.kord.core.behavior.interaction.response.DeferredPublicMessageInteractionResponseBehavior
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.entity.interaction.ChatInputCommandInteraction
import dev.kord.rest.builder.interaction.GlobalMultiApplicationCommandBuilder
import es.wokis.commands.CommandsEnum
import es.wokis.commands.test.TestCommand

interface CommandHandlerService {

    fun onRegisterCommand(commandBuilder: GlobalMultiApplicationCommandBuilder)

    suspend fun onExecute(
        interaction: ChatInputCommandInteraction,
        response: DeferredPublicMessageInteractionResponseBehavior
    )
}

class CommandHandlerServiceImpl(
    private val testCommand: TestCommand
) : CommandHandlerService {

    override fun onRegisterCommand(commandBuilder: GlobalMultiApplicationCommandBuilder) {
        testCommand.onRegisterCommand(commandBuilder)
    }

    override suspend fun onExecute(
        interaction: ChatInputCommandInteraction,
        response: DeferredPublicMessageInteractionResponseBehavior
    ) {
        val commandName = interaction.command.rootName
        when (CommandsEnum.forCommandName(commandName)) {
            CommandsEnum.TEST -> testCommand.onExecute(interaction, response)

            null -> respondUnknownCommand(response, commandName)
        }
    }

    private suspend fun respondUnknownCommand(
        response: DeferredPublicMessageInteractionResponseBehavior,
        commandName: String
    ) {
        response.respond {
            content = "Unknown or unregistered command, command name: $commandName"
        }
    }
}
