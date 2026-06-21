package commands.radio.subcommands.random

import dev.kord.common.Locale
import dev.kord.core.entity.interaction.ChatInputCommandInteraction
import es.wokis.commands.radio.subcommands.random.RadioRandomCommand
import es.wokis.data.radio.RadioDTO
import es.wokis.data.response.ErrorType
import es.wokis.data.response.RemoteResponse
import es.wokis.localization.LocalizationKeys
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

class RadioRandomCommandTest {

    private val radioService: RadioService = mockk()
    private val guildQueueService: GuildQueueService = mockk()
    private val localizationService: LocalizationService = mockk()

    private val radioRandomCommand = RadioRandomCommand(
        radioService = radioService,
        guildQueueService = guildQueueService,
        localizationService = localizationService
    )

    @Test
    fun `Given successful random radio When onExecute Then play radio`() = runTest {
        // Given
        val radio = RadioDTO(
            radioName = "Test Radio",
            url = "http://test.radio/stream",
            thumbnailUrl = "http://test.radio/icon.png",
            countryCode = "US"
        )
        val interaction = mockk<ChatInputCommandInteraction> {
            every { kord } returns mockedKord
            every { guildLocale } returns Locale.ENGLISH_UNITED_STATES
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
            radioService.getRandomRadio()
        } returns RemoteResponse.Success(radio)

        coEvery { localizationService.getString(any(), any(), any()) } returns "Searching..."

        // When
        radioRandomCommand.onExecute(interaction, mockedResponse)

        // Then
        coVerify(exactly = 1) {
            radioService.getRandomRadio()
            guildQueueService.getOrCreateLavaPlayerService(interaction)
            lavaPlayerService.playRadio(
                radioName = "Test Radio",
                radioUrl = "http://test.radio/stream",
                customFavicon = "http://test.radio/icon.png"
            )
        }
    }

    @Test
    fun `Given success with null data When onExecute Then show error message via edit`() = runTest {
        // Regression test: Success with null data must edit the original response,
        // not send a second response.respond.
        // Given
        val interaction = mockk<ChatInputCommandInteraction> {
            every { kord } returns mockedKord
            every { guildLocale } returns Locale.ENGLISH_UNITED_STATES
            every { data } returns mockk {
                every { guildId.value } returns null
            }
        }
        val lavaPlayerService = mockk<GuildLavaPlayerService>()

        coEvery {
            guildQueueService.getOrCreateLavaPlayerService(interaction)
        } returns lavaPlayerService

        coEvery {
            radioService.getRandomRadio()
        } returns RemoteResponse.Success(data = null)

        coEvery { localizationService.getString(any(), any(), any()) } returns "Error"

        // When
        radioRandomCommand.onExecute(interaction, mockedResponse)

        // Then
        coVerify(exactly = 1) {
            radioService.getRandomRadio()
        }
        coVerify(exactly = 0) {
            lavaPlayerService.playRadio(any(), any(), any())
        }
        // SEARCHING is called once via response.respond, ERROR is called via edit
        coVerify(exactly = 1) {
            localizationService.getString(
                key = LocalizationKeys.RADIO_RANDOM_SEARCHING,
                guildId = null,
                discordLocale = Locale.ENGLISH_UNITED_STATES
            )
        }
        coVerify(exactly = 1) {
            localizationService.getString(
                key = LocalizationKeys.RADIO_RANDOM_ERROR,
                guildId = null,
                discordLocale = Locale.ENGLISH_UNITED_STATES
            )
        }
    }

    @Test
    fun `Given error response When onExecute Then show error message`() = runTest {
        // Given
        val interaction = mockk<ChatInputCommandInteraction> {
            every { kord } returns mockedKord
            every { guildLocale } returns Locale.ENGLISH_UNITED_STATES
            every { data } returns mockk {
                every { guildId.value } returns null
            }
        }
        val lavaPlayerService = mockk<GuildLavaPlayerService>()

        coEvery {
            guildQueueService.getOrCreateLavaPlayerService(interaction)
        } returns lavaPlayerService

        coEvery {
            radioService.getRandomRadio()
        } returns RemoteResponse.Error(ErrorType.UnknownError(Exception("API Error"), "API Error"))

        coEvery { localizationService.getString(any(), any(), any()) } returns "Error"

        // When
        radioRandomCommand.onExecute(interaction, mockedResponse)

        // Then
        coVerify(exactly = 1) {
            radioService.getRandomRadio()
        }
        // response.respond is called exactly once (for SEARCHING), then edit is used for error
        coVerify(exactly = 1) {
            localizationService.getString(
                key = LocalizationKeys.RADIO_RANDOM_SEARCHING,
                guildId = null,
                discordLocale = Locale.ENGLISH_UNITED_STATES
            )
        }
        coVerify(exactly = 1) {
            localizationService.getString(
                key = LocalizationKeys.RADIO_RANDOM_ERROR,
                guildId = null,
                discordLocale = Locale.ENGLISH_UNITED_STATES
            )
        }
        coVerify(exactly = 0) {
            lavaPlayerService.playRadio(any(), any(), any())
        }
    }
}
