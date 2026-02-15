package services.commands

import dev.kord.core.behavior.interaction.response.DeferredPublicMessageInteractionResponseBehavior
import dev.kord.core.entity.interaction.ButtonInteraction
import dev.kord.core.entity.interaction.ChatInputCommandInteraction
import dev.kord.rest.builder.interaction.GlobalMultiApplicationCommandBuilder
import es.wokis.commands.CommandName
import es.wokis.commands.ComponentsEnum
import es.wokis.commands.config.ConfigGroupCommand
import es.wokis.commands.queue.QueueCommand
import commands.play.PlayCommand
import es.wokis.commands.player.PlayerCommand
import es.wokis.commands.radio.RadioGroupCommand
import es.wokis.commands.shuffle.ShuffleCommand
import es.wokis.commands.skip.SkipCommand
import dev.kord.core.entity.interaction.AutoCompleteInteraction
import es.wokis.commands.next.NextCommand
import es.wokis.commands.disconnect.DisconnectCommand
import es.wokis.commands.sounds.SoundsCommand
import es.wokis.commands.sound.SoundCommand
import es.wokis.commands.reconnect.ReconnectCommand
import es.wokis.commands.tts.TTSCommand
import es.wokis.commands.locale.LocaleCommand
import es.wokis.services.commands.CommandHandlerServiceImpl
import es.wokis.services.error.ErrorHandlerService
import es.wokis.services.localization.LocalizationService
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class CommandHandlerServiceTest {

    private val playCommand: PlayCommand = mockk()
    private val soundCommand: SoundCommand = mockk()
    private val queueCommand: QueueCommand = mockk()
    private val skipCommand: SkipCommand = mockk()
    private val shuffleCommand: ShuffleCommand = mockk()
    private val ttsCommand: TTSCommand = mockk()
    private val playerCommand: PlayerCommand = mockk()
    private val soundsCommand: SoundsCommand = mockk()
    private val reconnectCommand: ReconnectCommand = mockk()
    private val nextCommand: NextCommand = mockk()
    private val disconnectCommand: DisconnectCommand = mockk()
    private val localeCommand: LocaleCommand = mockk()
    private val localizationService: LocalizationService = mockk()
    private val radioGroupCommand: RadioGroupCommand = mockk()
    private val configGroupCommand: ConfigGroupCommand = mockk()
    private val errorHandlerService: ErrorHandlerService = mockk()

    private val commandHandlerService = CommandHandlerServiceImpl(
        playCommand = playCommand,
        soundCommand = soundCommand,
        localizationService = localizationService,
        queueCommand = queueCommand,
        skipCommand = skipCommand,
        shuffleCommand = shuffleCommand,
        ttsCommand = ttsCommand,
        playerCommand = playerCommand,
        soundsCommand = soundsCommand,
        radioGroupCommand = radioGroupCommand,
        configGroupCommand = configGroupCommand,
        reconnectCommand = reconnectCommand,
        nextCommand = nextCommand,
        disconnectCommand = disconnectCommand,
        localeCommand = localeCommand,
        errorHandlerService = errorHandlerService
    )

    @Test
    fun `When onRegisterSimpleCommand is called Then register all commands`() {
        // Given
        val commandBuilder: GlobalMultiApplicationCommandBuilder = mockk {
            every { commands } returns mockk {
                every { add(any()) } returns true
            }
        }
        justRun { playCommand.onRegisterCommand(any()) }
        justRun { soundCommand.onRegisterCommand(any()) }
        justRun { queueCommand.onRegisterCommand(any()) }
        justRun { skipCommand.onRegisterCommand(any()) }
        justRun { shuffleCommand.onRegisterCommand(any()) }
        justRun { ttsCommand.onRegisterCommand(any()) }
        justRun { playerCommand.onRegisterCommand(any()) }
        justRun { soundsCommand.onRegisterCommand(any()) }
        justRun { reconnectCommand.onRegisterCommand(any()) }
        justRun { nextCommand.onRegisterCommand(any()) }
        justRun { disconnectCommand.onRegisterCommand(any()) }
        justRun { localeCommand.onRegisterCommand(any()) }

        // When
        commandHandlerService.onRegisterSimpleCommand(commandBuilder)

        // Then
        verify(exactly = 1) {
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
    }

    @Test
    fun `Given test command When onExecute is called Then execute TestCommand`() = runTest {
        // Given
        val commandName = CommandName.Play.commandName
        val interaction: ChatInputCommandInteraction = mockk {
            every { command } returns mockk {
                every { rootName } returns commandName
            }
        }
        val response: DeferredPublicMessageInteractionResponseBehavior = mockk()
        coJustRun { playCommand.onExecute(any(), any()) }

        // When
        commandHandlerService.onExecute(interaction, response)

        // Then
        coVerify(exactly = 1) {
            playCommand.onExecute(interaction, response)
        }
    }

    @Test
    fun `Given queue command When onExecute is called Then execute QueueCommand`() = runTest {
        // Given
        val commandName = CommandName.Queue.commandName
        val interaction: ChatInputCommandInteraction = mockk {
            every { command } returns mockk {
                every { rootName } returns commandName
            }
        }
        val response: DeferredPublicMessageInteractionResponseBehavior = mockk()
        coJustRun { queueCommand.onExecute(any(), any()) }

        // When
        commandHandlerService.onExecute(interaction, response)

        // Then
        coVerify(exactly = 1) {
            queueCommand.onExecute(interaction, response)
        }
    }

    @Test
    fun `Given skip command When onExecute is called Then execute SkipCommand`() = runTest {
        // Given
        val commandName = CommandName.Skip.commandName
        val interaction: ChatInputCommandInteraction = mockk {
            every { command } returns mockk {
                every { rootName } returns commandName
            }
        }
        val response: DeferredPublicMessageInteractionResponseBehavior = mockk()
        coJustRun { skipCommand.onExecute(any(), any()) }

        // When
        commandHandlerService.onExecute(interaction, response)

        // Then
        coVerify(exactly = 1) {
            skipCommand.onExecute(interaction, response)
        }
    }

    @Test
    fun `Given shuffle command When onExecute is called Then execute ShuffleCommand`() = runTest {
        // Given
        val commandName = CommandName.Shuffle.commandName
        val interaction: ChatInputCommandInteraction = mockk {
            every { command } returns mockk {
                every { rootName } returns commandName
            }
        }
        val response: DeferredPublicMessageInteractionResponseBehavior = mockk()
        coJustRun { shuffleCommand.onExecute(any(), any()) }

        // When
        shuffleCommand.onExecute(interaction, response)

        // Then
        coVerify(exactly = 1) {
            shuffleCommand.onExecute(interaction, response)
        }
    }

    @Test
    fun `Given tts command When onExecute is called Then execute TTSCommand`() = runTest {
        // Given
        val commandName = CommandName.Tts.commandName
        val interaction: ChatInputCommandInteraction = mockk {
            every { command } returns mockk {
                every { rootName } returns commandName
            }
        }
        val response: DeferredPublicMessageInteractionResponseBehavior = mockk()
        coJustRun { ttsCommand.onExecute(any(), any()) }

        // When
        ttsCommand.onExecute(interaction, response)

        // Then
        coVerify(exactly = 1) {
            ttsCommand.onExecute(interaction, response)
        }
    }

    @Test
    fun `Given player command When onExecute is called Then execute PlayerCommand`() = runTest {
        // Given
        val commandName = CommandName.Player.commandName
        val interaction: ChatInputCommandInteraction = mockk {
            every { command } returns mockk {
                every { rootName } returns commandName
            }
        }
        val response: DeferredPublicMessageInteractionResponseBehavior = mockk()
        coJustRun { playerCommand.onExecute(any(), any()) }

        // When
        playerCommand.onExecute(interaction, response)

        // Then
        coVerify(exactly = 1) {
            playerCommand.onExecute(interaction, response)
        }
    }

    @Test
    fun `Given sounds command When onExecute is called Then execute SoundsCommand`() = runTest {
        // Given
        val commandName = CommandName.Sounds.commandName
        val interaction: ChatInputCommandInteraction = mockk {
            every { command } returns mockk {
                every { rootName } returns commandName
            }
        }
        val response: DeferredPublicMessageInteractionResponseBehavior = mockk()
        coJustRun { soundsCommand.onExecute(any(), any()) }

        // When
        soundsCommand.onExecute(interaction, response)

        // Then
        coVerify(exactly = 1) {
            soundsCommand.onExecute(interaction, response)
        }
    }

    @Test
    fun `Given reconnect command When onExecute is called Then execute ReconnectCommand`() = runTest {
        // Given
        val commandName = CommandName.Reconnect.commandName
        val interaction: ChatInputCommandInteraction = mockk {
            every { command } returns mockk {
                every { rootName } returns commandName
            }
        }
        val response: DeferredPublicMessageInteractionResponseBehavior = mockk()
        coJustRun { reconnectCommand.onExecute(any(), any()) }

        // When
        commandHandlerService.onExecute(interaction, response)

        // Then
        coVerify(exactly = 1) {
            reconnectCommand.onExecute(interaction, response)
        }
    }

    @Test
    fun `Given next command When onExecute is called Then execute NextCommand`() = runTest {
        // Given
        val commandName = CommandName.Next.commandName
        val interaction: ChatInputCommandInteraction = mockk {
            every { command } returns mockk {
                every { rootName } returns commandName
            }
        }
        val response: DeferredPublicMessageInteractionResponseBehavior = mockk()
        coJustRun { nextCommand.onExecute(any(), any()) }

        // When
        commandHandlerService.onExecute(interaction, response)

        // Then
        coVerify(exactly = 1) {
            nextCommand.onExecute(interaction, response)
        }
    }

    @Test
    fun `Given disconnect command When onExecute is called Then execute DisconnectCommand`() = runTest {
        // Given
        val commandName = CommandName.Disconnect.commandName
        val interaction: ChatInputCommandInteraction = mockk {
            every { command } returns mockk {
                every { rootName } returns commandName
            }
        }
        val response: DeferredPublicMessageInteractionResponseBehavior = mockk()
        coJustRun { disconnectCommand.onExecute(any(), any()) }

        // When
        commandHandlerService.onExecute(interaction, response)

        // Then
        coVerify(exactly = 1) {
            disconnectCommand.onExecute(interaction, response)
        }
    }

    @Test
    fun `Given locale command When onExecute is called Then execute LocaleCommand`() = runTest {
        // Given
        val commandName = CommandName.Locale.commandName
        val interaction: ChatInputCommandInteraction = mockk {
            every { command } returns mockk {
                every { rootName } returns commandName
            }
        }
        val response: DeferredPublicMessageInteractionResponseBehavior = mockk()
        coJustRun { localeCommand.onExecute(any(), any()) }

        // When
        commandHandlerService.onExecute(interaction, response)

        // Then
        coVerify(exactly = 1) {
            localeCommand.onExecute(interaction, response)
        }
    }

    @Test
    fun `Given queue previous interaction When onInteract is called Then execute QueueCommand onInteract`() = runTest {
        // Given
        val componentId = ComponentsEnum.QUEUE_PREVIOUS.customId
        val interaction: ButtonInteraction = mockk {
            every { component } returns mockk {
                every { customId } returns componentId
            }
        }
        coJustRun { queueCommand.onInteract(any()) }

        // When
        commandHandlerService.onInteract(interaction)

        // Then
        coVerify(exactly = 1) {
            queueCommand.onInteract(interaction)
        }
    }

    @Test
    fun `Given queue next interaction When onInteract is called Then execute QueueCommand onInteract`() = runTest {
        // Given
        val componentId = ComponentsEnum.QUEUE_NEXT.customId
        val interaction: ButtonInteraction = mockk {
            every { component } returns mockk {
                every { customId } returns componentId
            }
        }
        coJustRun { queueCommand.onInteract(any()) }

        // When
        commandHandlerService.onInteract(interaction)

        // Then
        coVerify(exactly = 1) {
            queueCommand.onInteract(interaction)
        }
    }

    @Test
    fun `Given sound previous interaction When onInteract is called Then execute QueueCommand onInteract`() = runTest {
        // Given
        val componentId = ComponentsEnum.SOUNDS_PREVIOUS.customId
        val interaction: ButtonInteraction = mockk {
            every { component } returns mockk {
                every { customId } returns componentId
            }
        }
        coJustRun { soundsCommand.onInteract(any()) }

        // When
        commandHandlerService.onInteract(interaction)

        // Then
        coVerify(exactly = 1) {
            soundsCommand.onInteract(interaction)
        }
    }

    @Test
    fun `Given sound next interaction When onInteract is called Then execute QueueCommand onInteract`() = runTest {
        // Given
        val componentId = ComponentsEnum.SOUNDS_NEXT.customId
        val interaction: ButtonInteraction = mockk {
            every { component } returns mockk {
                every { customId } returns componentId
            }
        }
        coJustRun { soundsCommand.onInteract(any()) }

        // When
        commandHandlerService.onInteract(interaction)

        // Then
        coVerify(exactly = 1) {
            soundsCommand.onInteract(interaction)
        }
    }

    @Test
    fun `Given sound autocomplete When onAutocomplete is called Then delegate to soundCommand`() = runTest {
        // Given
        val commandName = CommandName.Sound.commandName
        val interaction = mockk<AutoCompleteInteraction> {
            every { command.rootName } returns commandName
        }
        coJustRun { soundCommand.onAutoComplete(any()) }

        // When
        commandHandlerService.onAutocomplete(interaction)

        // Then
        coVerify(exactly = 1) {
            soundCommand.onAutoComplete(interaction)
        }
    }

    @Test
    fun `Given radio autocomplete When onAutocomplete is called Then delegate to radioGroupCommand`() = runTest {
        // Given
        val commandName = CommandName.Radio.commandName
        val interaction = mockk<AutoCompleteInteraction> {
            every { command.rootName } returns commandName
        }
        coJustRun { radioGroupCommand.onAutoComplete(any()) }

        // When
        commandHandlerService.onAutocomplete(interaction)

        // Then
        coVerify(exactly = 1) {
            radioGroupCommand.onAutoComplete(interaction)
        }
    }

    @Test
    fun `Given locale autocomplete When onAutocomplete is called Then delegate to localeCommand`() = runTest {
        // Given
        val commandName = CommandName.Locale.commandName
        val interaction = mockk<AutoCompleteInteraction> {
            every { command.rootName } returns commandName
        }
        coJustRun { localeCommand.onAutoComplete(any()) }

        // When
        commandHandlerService.onAutocomplete(interaction)

        // Then
        coVerify(exactly = 1) {
            localeCommand.onAutoComplete(interaction)
        }
    }
}
