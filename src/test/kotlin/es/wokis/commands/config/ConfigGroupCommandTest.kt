package es.wokis.commands.config

import dev.kord.common.Locale
import dev.kord.core.entity.interaction.AutoCompleteInteraction
import dev.kord.core.entity.interaction.ChatInputCommandInteraction
import dev.kord.core.entity.interaction.SubCommand
import es.wokis.commands.CommandName
import es.wokis.services.localization.LocalizationService
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import mock.mockedResponse
import org.junit.jupiter.api.Test

class ConfigGroupCommandTest {

    private val configReloadCommand: ConfigReloadCommand = mockk {
        coJustRun { onExecute(any(), any()) }
    }
    private val configSetCommand: ConfigSetCommand = mockk {
        coJustRun { onExecute(any(), any()) }
    }
    private val configGetCommand: ConfigGetCommand = mockk {
        coJustRun { onExecute(any(), any()) }
        coJustRun { onAutoComplete(any()) }
    }
    private val localizationService: LocalizationService = mockk {
        coEvery { getString(any(), any(), any()) } returns "Error message"
        every { getLocalizations(any()) } returns mutableMapOf()
    }

    private val configGroupCommand = ConfigGroupCommand(
        configReloadCommand = configReloadCommand,
        configSetCommand = configSetCommand,
        configGetCommand = configGetCommand,
        localizationService = localizationService
    )

    @Test
    fun `Given get subcommand When onExecute Then delegate to config get command`() = runTest {
        // Given
        val commandName = CommandName.Config.Get.commandName
        val interaction = mockk<ChatInputCommandInteraction> {
            every { command } returns mockk<SubCommand> {
                every { name } returns commandName
            }
            every { data } returns mockk {
                every { guildId.value } returns null
            }
            every { guildLocale } returns Locale.ENGLISH_UNITED_STATES
        }

        // When
        configGroupCommand.onExecute(interaction, mockedResponse)

        // Then
        coVerify(exactly = 1) {
            configGetCommand.onExecute(interaction, mockedResponse)
        }
    }

    @Test
    fun `Given set subcommand When onExecute Then delegate to config set command`() = runTest {
        // Given
        val commandName = CommandName.Config.Set.commandName
        val interaction = mockk<ChatInputCommandInteraction> {
            every { command } returns mockk<SubCommand> {
                every { name } returns commandName
            }
            every { data } returns mockk {
                every { guildId.value } returns null
            }
            every { guildLocale } returns Locale.ENGLISH_UNITED_STATES
        }

        // When
        configGroupCommand.onExecute(interaction, mockedResponse)

        // Then
        coVerify(exactly = 1) {
            configSetCommand.onExecute(interaction, mockedResponse)
        }
    }

    @Test
    fun `Given reload subcommand When onExecute Then delegate to config reload command`() = runTest {
        // Given
        val commandName = CommandName.Config.Reload.commandName
        val interaction = mockk<ChatInputCommandInteraction> {
            every { command } returns mockk<SubCommand> {
                every { name } returns commandName
            }
            every { data } returns mockk {
                every { guildId.value } returns null
            }
            every { guildLocale } returns Locale.ENGLISH_UNITED_STATES
        }

        // When
        configGroupCommand.onExecute(interaction, mockedResponse)

        // Then
        coVerify(exactly = 1) {
            configReloadCommand.onExecute(interaction, mockedResponse)
        }
    }

    @Test
    fun `Given unknown subcommand When onExecute Then do not delegate to any command`() = runTest {
        // Given
        val interaction = mockk<ChatInputCommandInteraction> {
            every { command } returns mockk<SubCommand> {
                every { name } returns "unknown"
            }
            every { data } returns mockk {
                every { guildId.value } returns null
            }
            every { guildLocale } returns Locale.ENGLISH_UNITED_STATES
        }

        // When
        configGroupCommand.onExecute(interaction, mockedResponse)

        // Then
        coVerify(exactly = 0) {
            configReloadCommand.onExecute(any(), any())
            configSetCommand.onExecute(any(), any())
            configGetCommand.onExecute(any(), any())
        }
        // Note: Current implementation silently ignores unknown subcommands
    }

    @Test
    fun `Given get subcommand When onAutoComplete Then delegate to config get command`() = runTest {
        // Given
        val commandName = CommandName.Config.Get.commandName
        val interaction = mockk<AutoCompleteInteraction> {
            every { command } returns mockk<SubCommand> {
                every { name } returns commandName
            }
        }

        // When
        configGroupCommand.onAutoComplete(interaction)

        // Then
        coVerify(exactly = 1) {
            configGetCommand.onAutoComplete(interaction)
        }
    }

    @Test
    fun `Given set subcommand When onAutoComplete Then do not delegate`() = runTest {
        // Given
        val commandName = CommandName.Config.Set.commandName
        val interaction = mockk<AutoCompleteInteraction> {
            every { command } returns mockk<SubCommand> {
                every { name } returns commandName
            }
        }

        // When
        configGroupCommand.onAutoComplete(interaction)

        // Then
        coVerify(exactly = 0) {
            configGetCommand.onAutoComplete(any())
        }
    }

    @Test
    fun `Given reload subcommand When onAutoComplete Then do not delegate`() = runTest {
        // Given
        val commandName = CommandName.Config.Reload.commandName
        val interaction = mockk<AutoCompleteInteraction> {
            every { command } returns mockk<SubCommand> {
                every { name } returns commandName
            }
        }

        // When
        configGroupCommand.onAutoComplete(interaction)

        // Then
        coVerify(exactly = 0) {
            configGetCommand.onAutoComplete(any())
        }
    }

    @Test
    fun `Given unknown subcommand When onAutoComplete Then do not delegate`() = runTest {
        // Given
        val interaction = mockk<AutoCompleteInteraction> {
            every { command } returns mockk<SubCommand> {
                every { name } returns "unknown"
            }
        }

        // When
        configGroupCommand.onAutoComplete(interaction)

        // Then
        coVerify(exactly = 0) {
            configGetCommand.onAutoComplete(any())
        }
    }
}
