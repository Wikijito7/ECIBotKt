package commands.radio.subcommands.search

import dev.kord.core.entity.interaction.AutoCompleteInteraction
import dev.kord.core.entity.interaction.ChatInputCommandInteraction
import dev.kord.core.entity.interaction.GroupCommand
import es.wokis.commands.CommandName
import es.wokis.commands.ComponentsEnum
import es.wokis.commands.radio.subcommands.search.RadioSearchCountryCodeCommand
import es.wokis.commands.radio.subcommands.search.RadioSearchGroupCommand
import es.wokis.commands.radio.subcommands.search.RadioSearchNameCommand
import es.wokis.localization.LocalizationKeys
import es.wokis.services.localization.LocalizationService
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import mock.mockedResponse
import org.junit.jupiter.api.Test

class RadioSearchGroupCommandTest {

    private val radioSearchNameCommand: RadioSearchNameCommand = mockk {
        coJustRun { onExecute(any(), any()) }
        coJustRun { onInteract(any()) }
    }
    private val radioSearchCountryCodeCommand: RadioSearchCountryCodeCommand = mockk {
        coJustRun { onExecute(any(), any()) }
        coJustRun { onInteract(any()) }
        coJustRun { onAutoComplete(any()) }
    }
    private val localizationService: LocalizationService = mockk {
        coEvery { getString(any(), any(), any()) } returns "Description"
        every { getLocalizations(any()) } returns mutableMapOf()
    }

    private val radioSearchGroupCommand = RadioSearchGroupCommand(
        radioSearchNameCommand = radioSearchNameCommand,
        radioSearchCountryCodeCommand = radioSearchCountryCodeCommand,
        localizationService = localizationService
    )

    @Test
    fun `Given search name subcommand When onExecute Then delegate to name command`() = runTest {
        // Given
        val commandName = CommandName.Radio.Search.Name.commandName
        val interaction = mockk<ChatInputCommandInteraction> {
            every { command } returns mockk<GroupCommand> {
                every { name } returns commandName
            }
        }

        // When
        radioSearchGroupCommand.onExecute(interaction, mockedResponse)

        // Then
        coVerify(exactly = 1) {
            radioSearchNameCommand.onExecute(interaction, mockedResponse)
        }
    }

    @Test
    fun `Given search country code subcommand When onExecute Then delegate to country code command`() = runTest {
        // Given
        val commandName = CommandName.Radio.Search.CountryCode.commandName
        val interaction = mockk<ChatInputCommandInteraction> {
            every { command } returns mockk<GroupCommand> {
                every { name } returns commandName
            }
        }

        // When
        radioSearchGroupCommand.onExecute(interaction, mockedResponse)

        // Then
        coVerify(exactly = 1) {
            radioSearchCountryCodeCommand.onExecute(interaction, mockedResponse)
        }
    }

    @Test
    fun `Given country code next button When onInteract Then delegate to country code command`() = runTest {
        // Given
        val customId = ComponentsEnum.RADIO_SEARCH_COUNTRY_CODE_NEXT.customId
        val interaction = mockk<dev.kord.core.entity.interaction.ButtonInteraction> {
            every { component } returns mockk {
                every { this@mockk.customId } returns customId
            }
        }

        // When
        radioSearchGroupCommand.onInteract(interaction)

        // Then - verify it's called
        coVerify(exactly = 1) {
            radioSearchCountryCodeCommand.onInteract(interaction)
        }
    }

    @Test
    fun `Given country code previous button When onInteract Then delegate to country code command`() = runTest {
        // Given
        val customId = ComponentsEnum.RADIO_SEARCH_COUNTRY_CODE_PREVIOUS.customId
        val interaction = mockk<dev.kord.core.entity.interaction.ButtonInteraction> {
            every { component } returns mockk {
                every { this@mockk.customId } returns customId
            }
        }

        // When
        radioSearchGroupCommand.onInteract(interaction)

        // Then
        coVerify(exactly = 1) {
            radioSearchCountryCodeCommand.onInteract(interaction)
        }
    }

    @Test
    fun `Given search name previous button When onInteract Then delegate to name command`() = runTest {
        // Given
        val customId = ComponentsEnum.RADIO_SEARCH_NAME_PREVIOUS.customId
        val interaction = mockk<dev.kord.core.entity.interaction.ButtonInteraction> {
            every { component } returns mockk {
                every { this@mockk.customId } returns customId
            }
        }

        // When
        radioSearchGroupCommand.onInteract(interaction)

        // Then
        coVerify(exactly = 1) {
            radioSearchNameCommand.onInteract(interaction)
        }
    }

    @Test
    fun `Given autocomplete When onAutoComplete Then delegate to country code command`() = runTest {
        // Given
        val interaction = mockk<AutoCompleteInteraction>()

        // When
        radioSearchGroupCommand.onAutoComplete(interaction)

        // Then
        coVerify(exactly = 1) {
            radioSearchCountryCodeCommand.onAutoComplete(interaction)
        }
    }

    @Test
    fun `Given unknown subcommand When onExecute Then do nothing`() = runTest {
        // Given
        val interaction = mockk<ChatInputCommandInteraction> {
            every { command } returns mockk<GroupCommand> {
                every { name } returns "unknown"
            }
        }

        // When
        radioSearchGroupCommand.onExecute(interaction, mockedResponse)

        // Then
        coVerify(exactly = 0) {
            radioSearchNameCommand.onExecute(any(), any())
            radioSearchCountryCodeCommand.onExecute(any(), any())
        }
    }
}
