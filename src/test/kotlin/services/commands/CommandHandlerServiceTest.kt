package services.commands

import dev.kord.core.behavior.interaction.response.DeferredPublicMessageInteractionResponseBehavior
import dev.kord.core.entity.interaction.ButtonInteraction
import dev.kord.core.entity.interaction.ChatInputCommandInteraction
import dev.kord.rest.builder.interaction.GlobalMultiApplicationCommandBuilder
import es.wokis.commands.CommandsEnum
import es.wokis.commands.ComponentsEnum
import es.wokis.commands.queue.QueueCommand
import commands.play.PlayCommand
import es.wokis.services.commands.CommandHandlerServiceImpl
import es.wokis.services.localization.LocalizationService
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class CommandHandlerServiceTest {

    private val playCommand: PlayCommand = mockk()
    private val queueCommand: QueueCommand = mockk()
    private val localizationService: LocalizationService = mockk()

    private val commandHandlerService = CommandHandlerServiceImpl(
        playCommand = playCommand,
        localizationService = localizationService,
        queueCommand = queueCommand
    )

    @Test
    fun `When onRegisterCommand is called Then register all commands`() {
        // Given
        val commandBuilder: GlobalMultiApplicationCommandBuilder = mockk {
            every { commands } returns mockk {
                every { add(any()) } returns true
            }
        }
        justRun { playCommand.onRegisterCommand(any()) }
        justRun { queueCommand.onRegisterCommand(any()) }

        // When
        commandHandlerService.onRegisterCommand(commandBuilder)

        // Then
        verify(exactly = 1) {
            playCommand.onRegisterCommand(commandBuilder)
        }
    }

    @Test
    fun `Given test command When onExecute is called Then execute TestCommand`() = runTest {
        // Given
        val commandName = CommandsEnum.PLAY.commandName
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
        val commandName = CommandsEnum.QUEUE.commandName
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
    fun `Given previous interaction When onInteract is called Then execute QueueCommand onInteract`() = runTest {
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
    fun `Given next interaction When onInteract is called Then execute QueueCommand onInteract`() = runTest {
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
}
