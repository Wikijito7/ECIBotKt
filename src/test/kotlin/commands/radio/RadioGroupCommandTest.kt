package commands.radio

import dev.kord.common.Locale
import dev.kord.core.entity.interaction.AutoCompleteInteraction
import dev.kord.core.entity.interaction.ButtonInteraction
import dev.kord.core.entity.interaction.ChatInputCommandInteraction
import dev.kord.core.entity.interaction.GroupCommand
import dev.kord.core.entity.interaction.SubCommand
import es.wokis.commands.CommandName
import es.wokis.commands.ComponentsEnum
import es.wokis.commands.radio.RadioGroupCommand
import es.wokis.commands.radio.subcommands.countrycodes.RadioCountryCodesCommand
import es.wokis.commands.radio.subcommands.list.RadioListCommand
import es.wokis.commands.radio.subcommands.play.RadioPlayCommand
import es.wokis.commands.radio.subcommands.random.RadioRandomCommand
import es.wokis.commands.radio.subcommands.search.RadioSearchGroupCommand
import es.wokis.services.localization.LocalizationService
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import mock.mockedResponse
import org.junit.jupiter.api.Test

class RadioGroupCommandTest {

    private val radioPlayCommand: RadioPlayCommand = mockk {
        coJustRun { onExecute(any(), any()) }
        coJustRun { onAutoComplete(any()) }
    }
    private val radioListCommand: RadioListCommand = mockk {
        coJustRun { onExecute(any(), any()) }
        coJustRun { onInteract(any()) }
    }
    private val radioSearchGroupCommand: RadioSearchGroupCommand = mockk {
        coJustRun { onExecute(any(), any()) }
        coJustRun { onInteract(any()) }
        coJustRun { onAutoComplete(any()) }
    }
    private val radioCountryCodesCommand: RadioCountryCodesCommand = mockk {
        coJustRun { onExecute(any(), any()) }
    }
    private val radioRandomCommand: RadioRandomCommand = mockk {
        coJustRun { onExecute(any(), any()) }
    }
    private val localizationService: LocalizationService = mockk {
        coEvery { getString(any(), any(), any()) } returns "Unknown command"
        coEvery { getString(any(), any(), any()) } returns "Description"
        every { getLocalizations(any()) } returns mutableMapOf()
    }

    private val radioGroupCommand = RadioGroupCommand(
        radioPlayCommand = radioPlayCommand,
        radioListCommand = radioListCommand,
        radioSearchGroupCommand = radioSearchGroupCommand,
        radioCountryCodesCommand = radioCountryCodesCommand,
        radioRandomCommand = radioRandomCommand,
        localizationService = localizationService
    )

    @Test
    fun `Given play subcommand When onExecute Then delegate to radio play command`() = runTest {
        // Given
        val commandName = CommandName.Radio.Play.commandName
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
        radioGroupCommand.onExecute(interaction, mockedResponse)

        // Then
        coVerify(exactly = 1) {
            radioPlayCommand.onExecute(interaction, mockedResponse)
        }
    }

    @Test
    fun `Given list subcommand When onExecute Then delegate to radio list command`() = runTest {
        // Given
        val commandName = CommandName.Radio.List.commandName
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
        radioGroupCommand.onExecute(interaction, mockedResponse)

        // Then
        coVerify(exactly = 1) {
            radioListCommand.onExecute(interaction, mockedResponse)
        }
    }

    @Test
    fun `Given search subcommand When onExecute Then delegate to radio search group command`() = runTest {
        // Given
        val commandName = CommandName.Radio.Search.commandName
        val interaction = mockk<ChatInputCommandInteraction> {
            every { command } returns mockk<GroupCommand> {
                every { groupName } returns commandName
            }
            every { data } returns mockk {
                every { guildId.value } returns null
            }
            every { guildLocale } returns Locale.ENGLISH_UNITED_STATES
        }

        // When
        radioGroupCommand.onExecute(interaction, mockedResponse)

        // Then
        coVerify(exactly = 1) {
            radioSearchGroupCommand.onExecute(interaction, mockedResponse)
        }
    }

    @Test
    fun `Given country codes subcommand When onExecute Then delegate to radio country codes command`() = runTest {
        // Given
        val commandName = CommandName.Radio.CountryCodes.commandName
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
        radioGroupCommand.onExecute(interaction, mockedResponse)

        // Then
        coVerify(exactly = 1) {
            radioCountryCodesCommand.onExecute(interaction, mockedResponse)
        }
    }

    @Test
    fun `Given random subcommand When onExecute Then delegate to radio random command`() = runTest {
        // Given
        val commandName = CommandName.Radio.Random.commandName
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
        radioGroupCommand.onExecute(interaction, mockedResponse)

        // Then
        coVerify(exactly = 1) {
            radioRandomCommand.onExecute(interaction, mockedResponse)
        }
    }

    @Test
    fun `Given unknown command When onExecute Then respond with unknown message`() = runTest {
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
        radioGroupCommand.onExecute(interaction, mockedResponse)

        // Then
        coVerify(exactly = 0) {
            radioPlayCommand.onExecute(any(), any())
            radioListCommand.onExecute(any(), any())
            radioSearchGroupCommand.onExecute(any(), any())
            radioCountryCodesCommand.onExecute(any(), any())
            radioRandomCommand.onExecute(any(), any())
        }
    }

    @Test
    fun `Given list next button When onInteract Then delegate to radio list command`() = runTest {
        // Given
        val customId = ComponentsEnum.RADIO_LIST_NEXT.customId
        val interaction = mockk<ButtonInteraction> {
            every { component } returns mockk {
                every { this@mockk.customId } returns customId
            }
        }

        // When
        radioGroupCommand.onInteract(interaction)

        // Then
        coVerify(exactly = 1) {
            radioListCommand.onInteract(interaction)
        }
    }

    @Test
    fun `Given list previous button When onInteract Then delegate to radio list command`() = runTest {
        // Given
        val customId = ComponentsEnum.RADIO_LIST_PREVIOUS.customId
        val interaction = mockk<ButtonInteraction> {
            every { component } returns mockk {
                every { this@mockk.customId } returns customId
            }
        }

        // When
        radioGroupCommand.onInteract(interaction)

        // Then
        coVerify(exactly = 1) {
            radioListCommand.onInteract(interaction)
        }
    }

    @Test
    fun `Given list next button with separator When onInteract Then delegate to radio list command`() = runTest {
        // Given
        val customId = "${ComponentsEnum.RADIO_LIST_NEXT.customId}::2"
        val interaction = mockk<ButtonInteraction> {
            every { component } returns mockk {
                every { this@mockk.customId } returns customId
            }
        }

        // When
        radioGroupCommand.onInteract(interaction)

        // Then
        coVerify(exactly = 1) {
            radioListCommand.onInteract(interaction)
        }
    }

    @Test
    fun `Given list previous button with separator When onInteract Then delegate to radio list command`() = runTest {
        // Given
        val customId = "${ComponentsEnum.RADIO_LIST_PREVIOUS.customId}::1"
        val interaction = mockk<ButtonInteraction> {
            every { component } returns mockk {
                every { this@mockk.customId } returns customId
            }
        }

        // When
        radioGroupCommand.onInteract(interaction)

        // Then
        coVerify(exactly = 1) {
            radioListCommand.onInteract(interaction)
        }
    }

    @Test
    fun `Given search name next button When onInteract Then delegate to radio search group command`() = runTest {
        // Given
        val customId = ComponentsEnum.RADIO_SEARCH_NAME_NEXT.customId
        val interaction = mockk<ButtonInteraction> {
            every { component } returns mockk {
                every { this@mockk.customId } returns customId
            }
        }

        // When
        radioGroupCommand.onInteract(interaction)

        // Then
        coVerify(exactly = 1) {
            radioSearchGroupCommand.onInteract(interaction)
        }
    }

    @Test
    fun `Given search name previous button When onInteract Then delegate to radio search group command`() = runTest {
        // Given
        val customId = ComponentsEnum.RADIO_SEARCH_NAME_PREVIOUS.customId
        val interaction = mockk<ButtonInteraction> {
            every { component } returns mockk {
                every { this@mockk.customId } returns customId
            }
        }

        // When
        radioGroupCommand.onInteract(interaction)

        // Then
        coVerify(exactly = 1) {
            radioSearchGroupCommand.onInteract(interaction)
        }
    }

    @Test
    fun `Given search country code next button When onInteract Then delegate to radio search group command`() = runTest {
        // Given
        val customId = ComponentsEnum.RADIO_SEARCH_COUNTRY_CODE_NEXT.customId
        val interaction = mockk<ButtonInteraction> {
            every { component } returns mockk {
                every { this@mockk.customId } returns customId
            }
        }

        // When
        radioGroupCommand.onInteract(interaction)

        // Then
        coVerify(exactly = 1) {
            radioSearchGroupCommand.onInteract(interaction)
        }
    }

    @Test
    fun `Given search country code previous button When onInteract Then delegate to radio search group command`() = runTest {
        // Given
        val customId = ComponentsEnum.RADIO_SEARCH_COUNTRY_CODE_PREVIOUS.customId
        val interaction = mockk<ButtonInteraction> {
            every { component } returns mockk {
                every { this@mockk.customId } returns customId
            }
        }

        // When
        radioGroupCommand.onInteract(interaction)

        // Then
        coVerify(exactly = 1) {
            radioSearchGroupCommand.onInteract(interaction)
        }
    }

    @Test
    fun `Given unknown button When onInteract Then do nothing`() = runTest {
        // Given
        val customId = "unknown_button"
        val interaction = mockk<ButtonInteraction> {
            every { component } returns mockk {
                every { this@mockk.customId } returns customId
            }
        }

        // When
        radioGroupCommand.onInteract(interaction)

        // Then
        coVerify(exactly = 0) {
            radioListCommand.onInteract(any())
            radioSearchGroupCommand.onInteract(any())
        }
    }

    @Test
    fun `Given autocomplete with subcommand When onAutoComplete Then delegate to radio play command`() = runTest {
        // Given
        val interaction = mockk<AutoCompleteInteraction> {
            every { command } returns mockk<SubCommand>()
        }

        // When
        radioGroupCommand.onAutoComplete(interaction)

        // Then
        coVerify(exactly = 1) {
            radioPlayCommand.onAutoComplete(interaction)
        }
    }

    @Test
    fun `Given autocomplete with group command When onAutoComplete Then delegate to radio search group command`() = runTest {
        // Given
        val interaction = mockk<AutoCompleteInteraction> {
            every { command } returns mockk<GroupCommand>()
        }

        // When
        radioGroupCommand.onAutoComplete(interaction)

        // Then
        coVerify(exactly = 1) {
            radioSearchGroupCommand.onAutoComplete(interaction)
        }
    }
}
