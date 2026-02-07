package es.wokis.services.commands

import dev.kord.common.Locale
import dev.kord.core.behavior.interaction.response.DeferredPublicMessageInteractionResponseBehavior
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.entity.interaction.ButtonInteraction
import dev.kord.core.entity.interaction.ChatInputCommandInteraction
import dev.kord.rest.builder.interaction.GlobalMultiApplicationCommandBuilder
import es.wokis.commands.CommandName
import es.wokis.commands.ComponentsEnum
import es.wokis.commands.queue.QueueCommand
import commands.play.PlayCommand
import dev.kord.core.Kord
import dev.kord.core.entity.interaction.AutoCompleteInteraction
import es.wokis.commands.player.PlayerCommand
import es.wokis.commands.radio.RadioGroupCommand
import es.wokis.commands.next.NextCommand
import es.wokis.commands.disconnect.DisconnectCommand
import es.wokis.commands.reconnect.ReconnectCommand
import es.wokis.commands.locale.LocaleCommand
import es.wokis.commands.shuffle.ShuffleCommand
import es.wokis.commands.skip.SkipCommand
import es.wokis.commands.sound.SoundCommand
import es.wokis.commands.sounds.SoundsCommand
import es.wokis.commands.tts.TTSCommand
import es.wokis.constants.CUSTOM_COMPONENT_SEPARATOR
import es.wokis.localization.LocalizationKeys
import es.wokis.services.error.ErrorHandlerService
import es.wokis.services.localization.LocalizationService

interface CommandHandlerService {

    fun onRegisterSimpleCommand(commandBuilder: GlobalMultiApplicationCommandBuilder)

    suspend fun onRegisterGroupCommand(kord: Kord)

    suspend fun onExecute(
        interaction: ChatInputCommandInteraction,
        response: DeferredPublicMessageInteractionResponseBehavior
    )

    suspend fun onInteract(interaction: ButtonInteraction)

    suspend fun onAutocomplete(interaction: AutoCompleteInteraction)
}

class CommandHandlerServiceImpl(
    private val playCommand: PlayCommand,
    private val soundCommand: SoundCommand,
    private val queueCommand: QueueCommand,
    private val skipCommand: SkipCommand,
    private val shuffleCommand: ShuffleCommand,
    private val ttsCommand: TTSCommand,
    private val playerCommand: PlayerCommand,
    private val soundsCommand: SoundsCommand,
    private val reconnectCommand: ReconnectCommand,
    private val nextCommand: NextCommand,
    private val disconnectCommand: DisconnectCommand,
    private val localeCommand: LocaleCommand,
    private val radioGroupCommand: RadioGroupCommand,
    private val localizationService: LocalizationService,
    private val errorHandlerService: ErrorHandlerService
) : CommandHandlerService {

    override fun onRegisterSimpleCommand(commandBuilder: GlobalMultiApplicationCommandBuilder) {
        playCommand.onRegisterCommand(commandBuilder)
        soundCommand.onRegisterCommand(commandBuilder)
        queueCommand.onRegisterCommand(commandBuilder)
        skipCommand.onRegisterCommand(commandBuilder)
        shuffleCommand.onRegisterCommand(commandBuilder)
        ttsCommand.onRegisterCommand(commandBuilder)
        playerCommand.onRegisterCommand(commandBuilder)
        soundsCommand.onRegisterCommand(commandBuilder)
        reconnectCommand.onRegisterCommand(commandBuilder)
        nextCommand.onRegisterCommand(commandBuilder)
        disconnectCommand.onRegisterCommand(commandBuilder)
        localeCommand.onRegisterCommand(commandBuilder)
    }

    override suspend fun onRegisterGroupCommand(kord: Kord) {
        radioGroupCommand.onRegisterCommand(kord)
    }

    override suspend fun onExecute(
        interaction: ChatInputCommandInteraction,
        response: DeferredPublicMessageInteractionResponseBehavior
    ) {
        val commandName = interaction.command.rootName
        try {
            when (commandName) {
                CommandName.Play.commandName -> playCommand.onExecute(interaction, response)
                CommandName.Sound.commandName -> soundCommand.onExecute(interaction, response)
                CommandName.Queue.commandName -> queueCommand.onExecute(interaction, response)
                CommandName.Skip.commandName -> skipCommand.onExecute(interaction, response)
                CommandName.Shuffle.commandName -> shuffleCommand.onExecute(interaction, response)
                CommandName.Tts.commandName -> ttsCommand.onExecute(interaction, response)
                CommandName.Player.commandName -> playerCommand.onExecute(interaction, response)
                CommandName.Sounds.commandName -> soundsCommand.onExecute(interaction, response)
                CommandName.Radio.commandName -> radioGroupCommand.onExecute(interaction, response)
                CommandName.Reconnect.commandName -> reconnectCommand.onExecute(interaction, response)
                CommandName.Next.commandName -> nextCommand.onExecute(interaction, response)
                CommandName.Disconnect.commandName -> disconnectCommand.onExecute(interaction, response)
                CommandName.Locale.commandName -> localeCommand.onExecute(interaction, response)
                else -> respondUnknownCommand(response, interaction.guildLocale, commandName)
            }
        } catch (exception: Throwable) {
            errorHandlerService.handleCommandError(exception, interaction, response, commandName)
        }
    }

    override suspend fun onInteract(
        interaction: ButtonInteraction
    ) {
        val customId = interaction.component.customId?.split(CUSTOM_COMPONENT_SEPARATOR)?.firstOrNull() ?: return
        val commandName = getCommandNameFromComponent(customId)

        try {
            when (ComponentsEnum.forCustomId(customId)) {
                ComponentsEnum.QUEUE_NEXT, ComponentsEnum.QUEUE_PREVIOUS -> queueCommand.onInteract(interaction)

                ComponentsEnum.PLAYER_RESUME, ComponentsEnum.PLAYER_PAUSE, ComponentsEnum.PLAYER_SKIP,
                ComponentsEnum.PLAYER_DISCONNECT, ComponentsEnum.PLAYER_SHUFFLE, ComponentsEnum.PLAYER_RECONNECT -> playerCommand.onInteract(interaction)

                ComponentsEnum.SOUNDS_NEXT, ComponentsEnum.SOUNDS_PREVIOUS -> soundsCommand.onInteract(interaction)

                ComponentsEnum.RADIO_LIST_NEXT, ComponentsEnum.RADIO_LIST_PREVIOUS, ComponentsEnum.RADIO_SEARCH_NAME_NEXT,
                ComponentsEnum.RADIO_SEARCH_NAME_PREVIOUS, ComponentsEnum.RADIO_SEARCH_COUNTRY_CODE_NEXT,
                ComponentsEnum.RADIO_SEARCH_COUNTRY_CODE_PREVIOUS, ComponentsEnum.RADIO_COUNTRYCODES_NEXT,
                ComponentsEnum.RADIO_COUNTRYCODES_PREVIOUS -> radioGroupCommand.onInteract(interaction)

                null -> Unit
            }
        } catch (exception: Throwable) {
            errorHandlerService.handleInteractionError(exception, interaction, commandName)
        }
    }

    override suspend fun onAutocomplete(interaction: AutoCompleteInteraction) {
        val commandName = interaction.command.rootName
        try {
            when (commandName) {
                CommandName.Sound.commandName -> soundCommand.onAutoComplete(interaction)
                CommandName.Radio.commandName -> radioGroupCommand.onAutoComplete(interaction)
                CommandName.Locale.commandName -> localeCommand.onAutoComplete(interaction)
            }
        } catch (exception: Throwable) {
            errorHandlerService.handleAutocompleteError(exception, interaction, commandName)
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

    private fun getCommandNameFromComponent(customId: String): String? = when (ComponentsEnum.forCustomId(customId)) {
        ComponentsEnum.QUEUE_NEXT, ComponentsEnum.QUEUE_PREVIOUS -> CommandName.Queue.commandName

        ComponentsEnum.PLAYER_RESUME, ComponentsEnum.PLAYER_PAUSE, ComponentsEnum.PLAYER_SKIP,
        ComponentsEnum.PLAYER_DISCONNECT, ComponentsEnum.PLAYER_SHUFFLE, ComponentsEnum.PLAYER_RECONNECT -> CommandName.Player.commandName

        ComponentsEnum.SOUNDS_NEXT, ComponentsEnum.SOUNDS_PREVIOUS -> CommandName.Sounds.commandName

        ComponentsEnum.RADIO_LIST_NEXT, ComponentsEnum.RADIO_LIST_PREVIOUS, ComponentsEnum.RADIO_SEARCH_NAME_NEXT,
        ComponentsEnum.RADIO_SEARCH_NAME_PREVIOUS, ComponentsEnum.RADIO_SEARCH_COUNTRY_CODE_NEXT,
        ComponentsEnum.RADIO_SEARCH_COUNTRY_CODE_PREVIOUS, ComponentsEnum.RADIO_COUNTRYCODES_NEXT,
        ComponentsEnum.RADIO_COUNTRYCODES_PREVIOUS -> CommandName.Radio.commandName

        null -> null
    }
}
