package es.wokis.services.commands

import dev.kord.common.Locale
import dev.kord.core.behavior.interaction.response.DeferredPublicMessageInteractionResponseBehavior
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.entity.interaction.ButtonInteraction
import dev.kord.core.entity.interaction.ChatInputCommandInteraction
import dev.kord.rest.builder.interaction.GlobalMultiApplicationCommandBuilder
import es.wokis.commands.CommandsEnum
import es.wokis.commands.ComponentsEnum
import es.wokis.commands.queue.QueueCommand
import commands.play.PlayCommand
import es.wokis.commands.shuffle.ShuffleCommand
import es.wokis.commands.skip.SkipCommand
import es.wokis.commands.tts.TTSCommand
import es.wokis.localization.LocalizationKeys
import es.wokis.services.localization.LocalizationService

interface CommandHandlerService {

    fun onRegisterCommand(commandBuilder: GlobalMultiApplicationCommandBuilder)

    suspend fun onExecute(
        interaction: ChatInputCommandInteraction,
        response: DeferredPublicMessageInteractionResponseBehavior
    )

    suspend fun onInteract(interaction: ButtonInteraction)
}

class CommandHandlerServiceImpl(
    private val playCommand: PlayCommand,
    private val queueCommand: QueueCommand,
    private val skipCommand: SkipCommand,
    private val shuffleCommand: ShuffleCommand,
    private val ttsCommand: TTSCommand,
    private val localizationService: LocalizationService
) : CommandHandlerService {

    override fun onRegisterCommand(commandBuilder: GlobalMultiApplicationCommandBuilder) {
        playCommand.onRegisterCommand(commandBuilder)
        queueCommand.onRegisterCommand(commandBuilder)
        skipCommand.onRegisterCommand(commandBuilder)
        shuffleCommand.onRegisterCommand(commandBuilder)
        ttsCommand.onRegisterCommand(commandBuilder)
    }

    override suspend fun onExecute(
        interaction: ChatInputCommandInteraction,
        response: DeferredPublicMessageInteractionResponseBehavior
    ) {
        val commandName = interaction.command.rootName
        when (CommandsEnum.forCommandName(commandName)) {
            CommandsEnum.PLAY -> playCommand.onExecute(interaction, response)

            CommandsEnum.QUEUE -> queueCommand.onExecute(interaction, response)

            CommandsEnum.SKIP -> skipCommand.onExecute(interaction, response)

            CommandsEnum.SHUFFLE -> shuffleCommand.onExecute(interaction, response)

            CommandsEnum.TTS -> ttsCommand.onExecute(interaction, response)

            null -> respondUnknownCommand(response, interaction.guildLocale, commandName)
        }
    }

    override suspend fun onInteract(
        interaction: ButtonInteraction
    ) {
        val customId = interaction.component.customId ?: return
        when (ComponentsEnum.forCustomId(customId)) {
            ComponentsEnum.QUEUE_NEXT, ComponentsEnum.QUEUE_PREVIOUS -> queueCommand.onInteract(interaction)

            null -> Unit
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
