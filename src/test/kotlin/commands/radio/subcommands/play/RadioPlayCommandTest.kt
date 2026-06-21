package commands.radio.subcommands.play

import dev.kord.common.Locale
import dev.kord.common.entity.Snowflake
import dev.kord.core.entity.interaction.AutoCompleteInteraction
import dev.kord.core.entity.interaction.ChatInputCommandInteraction
import es.wokis.commands.radio.subcommands.play.RadioPlayCommand
import es.wokis.data.radio.RadioDTO
import es.wokis.data.response.RemoteResponse
import es.wokis.services.lavaplayer.GuildLavaPlayerService
import es.wokis.services.localization.LocalizationService
import es.wokis.services.queue.GuildQueueService
import es.wokis.services.radio.RadioService
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import mock.mockedKord
import mock.mockedResponse
import org.junit.jupiter.api.Test

class RadioPlayCommandTest {

    private val radioService: RadioService = mockk()
    private val guildQueueService: GuildQueueService = mockk()
    private val localizationService: LocalizationService = mockk()

    private val radioPlayCommand = RadioPlayCommand(
        radioService = radioService,
        guildQueueService = guildQueueService,
        localizationService = localizationService
    )

    @Test
    fun `Given valid radio name When onExecute Then play radio`() = runTest {
        // Given
        val radio = RadioDTO(
            radioName = "Test Radio",
            url = "http://test.radio/stream",
            thumbnailUrl = "http://test.radio/icon.png",
            countryCode = "US"
        )
        val radioName = "Test Radio"
        val interaction = mockk<ChatInputCommandInteraction> {
            every { kord } returns mockedKord
            every { guildLocale } returns Locale.ENGLISH_UNITED_STATES
            every { command.strings["radio"] } returns radioName
            every { data } returns mockk {
                every { guildId.value } returns null
            }
        }
        val lavaPlayerService = mockk<GuildLavaPlayerService> {
            coJustRun { playRadio(any(), any(), any()) }
        }

        coEvery {
            guildQueueService.getOrCreateLavaPlayerService(interaction)
        } returns lavaPlayerService

        coEvery {
            radioService.findRadio(radioName)
        } returns radio

        coEvery { localizationService.getStringFormat(any(), any(), any(), *anyVararg()) } returns "Searching..."
        coEvery { localizationService.getString(any(), any(), any()) } returns "Radio found!"

        // When
        radioPlayCommand.onExecute(interaction, mockedResponse)

        // Then
        coVerify(exactly = 1) {
            radioService.findRadio(radioName)
            guildQueueService.getOrCreateLavaPlayerService(interaction)
            lavaPlayerService.playRadio(
                radioName = "Test Radio",
                radioUrl = "http://test.radio/stream",
                customFavicon = "http://test.radio/icon.png"
            )
        }
    }

    @Test
    fun `Given radio not found When onExecute Then show not found message`() = runTest {
        // Given
        val radioName = "NonExistentRadio"
        val interaction = mockk<ChatInputCommandInteraction> {
            every { kord } returns mockedKord
            every { guildLocale } returns Locale.ENGLISH_UNITED_STATES
            every { command.strings["radio"] } returns radioName
            every { data } returns mockk {
                every { guildId.value } returns null
            }
        }
        val lavaPlayerService = mockk<GuildLavaPlayerService>()

        coEvery {
            guildQueueService.getOrCreateLavaPlayerService(interaction)
        } returns lavaPlayerService

        coEvery {
            radioService.findRadio(radioName)
        } returns null

        coEvery { localizationService.getStringFormat(any(), any(), any(), *anyVararg()) } returns "Searching..."
        coEvery { localizationService.getString(any(), any(), any()) } returns "Radio not found"

        // When
        radioPlayCommand.onExecute(interaction, mockedResponse)

        // Then
        coVerify(exactly = 1) {
            radioService.findRadio(radioName)
        }
    }

    @Test
    fun `Given empty radio name When onExecute Then show required message`() = runTest {
        // Given
        val interaction = mockk<ChatInputCommandInteraction> {
            every { kord } returns mockedKord
            every { guildLocale } returns Locale.ENGLISH_UNITED_STATES
            every { command.strings["radio"] } returns null
            every { data } returns mockk {
                every { guildId.value } returns null
            }
        }
        val lavaPlayerService = mockk<GuildLavaPlayerService>()

        coEvery {
            guildQueueService.getOrCreateLavaPlayerService(interaction)
        } returns lavaPlayerService

        coEvery { localizationService.getString(any(), any(), any()) } returns "Radio name is required"
        coEvery { localizationService.getStringFormat(any(), any(), any()) } returns "Radio name is required"
        coEvery { radioService.findRadio(any()) } returns null

        // When
        radioPlayCommand.onExecute(interaction, mockedResponse)

        // Then
        coVerify(exactly = 0) {
            radioService.findRadio(any())
        }
    }

    @Test
    fun `Given autocomplete with input When onAutoComplete Then return suggestions`() = runTest {
        // Given
        val input = "Test"
        val radios = listOf(
            RadioDTO(
                radioName = "Test Radio 1",
                url = "http://test1.radio/stream",
                thumbnailUrl = "http://test1.radio/icon.png",
                countryCode = "US"
            ),
            RadioDTO(
                radioName = "Test Radio 2",
                url = "http://test2.radio/stream",
                thumbnailUrl = "http://test2.radio/icon.png",
                countryCode = "ES"
            )
        )
        val interaction = mockk<AutoCompleteInteraction> {
            every { kord } returns mockedKord
            every { id } returns Snowflake(123456789)
            every { token } returns "test-token"
            every { command.strings["radio"] } returns input
            every { guildLocale } returns Locale.ENGLISH_UNITED_STATES
            every { data } returns mockk {
                every { guildId.value } returns null
            }
        }

        coEvery {
            radioService.searchRadio(input)
        } returns RemoteResponse.Success(radios)

        // When
        radioPlayCommand.onAutoComplete(interaction)

        // Then
        coVerify(exactly = 1) {
            radioService.searchRadio(input)
        }
    }

    @Test
    fun `Given autocomplete with empty input When onAutoComplete Then return empty list`() = runTest {
        // Given
        val interaction = mockk<AutoCompleteInteraction> {
            every { kord } returns mockedKord
            every { id } returns Snowflake(123456789)
            every { token } returns "test-token"
            every { command.strings["radio"] } returns ""
            every { guildLocale } returns Locale.ENGLISH_UNITED_STATES
            every { data } returns mockk {
                every { guildId.value } returns null
            }
        }

        // When
        radioPlayCommand.onAutoComplete(interaction)

        // Then
        coVerify(exactly = 0) {
            radioService.searchRadio(any())
        }
    }
}
