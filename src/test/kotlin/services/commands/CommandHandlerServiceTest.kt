package services.commands

import dev.kord.core.behavior.interaction.response.DeferredPublicMessageInteractionResponseBehavior
import dev.kord.core.entity.interaction.ChatInputCommandInteraction
import dev.kord.rest.builder.interaction.GlobalMultiApplicationCommandBuilder
import es.wokis.commands.CommandsEnum
import es.wokis.commands.queue.QueueCommand
import es.wokis.commands.test.TestCommand
import es.wokis.services.commands.CommandHandlerServiceImpl
import es.wokis.services.localization.LocalizationService
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class CommandHandlerServiceTest {

    private val testCommand: TestCommand = mockk()
    private val queueCommand: QueueCommand = mockk()
    private val localizationService: LocalizationService = mockk()

    private val commandHandlerService = CommandHandlerServiceImpl(
        testCommand = testCommand,
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
        justRun { testCommand.onRegisterCommand(any()) }
        justRun { queueCommand.onRegisterCommand(any()) }

        // When
        commandHandlerService.onRegisterCommand(commandBuilder)

        // Then
        verify(exactly = 1) {
            testCommand.onRegisterCommand(commandBuilder)
        }
    }

    @Test
    fun `Given test command When onExecute is called Then execute TestCommand`() = runTest {
        // Given
        val commandName = CommandsEnum.TEST.commandName
        val interaction: ChatInputCommandInteraction = mockk {
            every { command } returns mockk {
                every { rootName } returns commandName
            }
        }
        val response: DeferredPublicMessageInteractionResponseBehavior = mockk()
        coJustRun { testCommand.onExecute(any(), any()) }

        // When
        commandHandlerService.onExecute(interaction, response)

        // Then
        coVerify(exactly = 1) {
            testCommand.onExecute(interaction, response)
        }
    }
}
