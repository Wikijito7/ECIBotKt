package es.wokis.services.commands

import dev.kord.common.Locale
import dev.kord.core.behavior.interaction.response.DeferredPublicMessageInteractionResponseBehavior
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.entity.interaction.ChatInputCommandInteraction
import dev.kord.rest.builder.interaction.GlobalMultiApplicationCommandBuilder
import es.wokis.commands.CommandsEnum
import es.wokis.commands.test.TestCommand
import es.wokis.localization.LocalizationKeys
import es.wokis.services.localization.LocalizationService

interface CommandHandlerService {

    fun onRegisterCommand(commandBuilder: GlobalMultiApplicationCommandBuilder)

    suspend fun onExecute(
        interaction: ChatInputCommandInteraction,
        response: DeferredPublicMessageInteractionResponseBehavior
    )
}

class CommandHandlerServiceImpl(
    private val testCommand: TestCommand,
    private val localizationService: LocalizationService
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

            null -> respondUnknownCommand(response, interaction.guildLocale, commandName)
        }
    }

    private suspend fun respondUnknownCommand(
        response: DeferredPublicMessageInteractionResponseBehavior,
        locale: Locale?,
        commandName: String
    ) {
        response.respond {
            content = localizationService.getStringFormat(
                key = LocalizationKeys.UNKNOWN_COMMAND,
                locale = locale ?: Locale.ENGLISH_UNITED_STATES,
                arguments = arrayOf(commandName)
            )
        }
    }
}
