package commands.radio.subcommands.random

import dev.kord.common.Locale
import dev.kord.core.entity.interaction.ChatInputCommandInteraction
import es.wokis.commands.radio.subcommands.random.RadioRandomCommand
import es.wokis.data.radio.RadioDTO
import es.wokis.data.response.ErrorType
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

        every { localizationService.getString(any(), any()) } returns "Searching..."

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
    fun `Given error response When onExecute Then show error message`() = runTest {
        // Given
        val interaction = mockk<ChatInputCommandInteraction> {
            every { kord } returns mockedKord
            every { guildLocale } returns Locale.ENGLISH_UNITED_STATES
        }
        val lavaPlayerService = mockk<GuildLavaPlayerService>()

        coEvery {
            guildQueueService.getOrCreateLavaPlayerService(interaction)
        } returns lavaPlayerService

        coEvery {
            radioService.getRandomRadio()
        } returns RemoteResponse.Error(ErrorType.UnknownError(Exception("API Error"), "API Error"))

        every { localizationService.getString(any(), any()) } returns "Error"

        // When
        radioRandomCommand.onExecute(interaction, mockedResponse)

        // Then
        coVerify(exactly = 1) {
            radioService.getRandomRadio()
        }
    }
}
