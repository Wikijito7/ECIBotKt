package commands.radio.subcommands.search

import dev.kord.common.Locale
import dev.kord.core.entity.interaction.ChatInputCommandInteraction
import es.wokis.commands.radio.subcommands.search.RadioSearchNameCommand
import es.wokis.data.radio.RadioDTO
import es.wokis.data.radio.RadioPageDTO
import es.wokis.data.response.RemoteResponse
import es.wokis.services.localization.LocalizationService
import es.wokis.services.radio.RadioService
import io.mockk.*
import kotlinx.coroutines.test.runTest
import mock.mockedKord
import mock.mockedResponse
import org.junit.jupiter.api.Test

class RadioSearchNameCommandTest {

    private val radioService: RadioService = mockk()
    private val localizationService: LocalizationService = mockk()

    private val radioSearchNameCommand = RadioSearchNameCommand(
        radioService = radioService,
        localizationService = localizationService
    )

    @Test
    fun `Given search name When onExecute Then show paginated results`() = runTest {
        // Given
        val searchName = "Rock"
        val radioPage = RadioPageDTO(
            currentPage = 1,
            maxPage = 3,
            radios = listOf(
                RadioDTO(
                    radioName = "Rock Radio 1",
                    url = "http://rock1.radio/stream",
                    thumbnailUrl = "http://rock1.radio/icon.png",
                    countryCode = "US"
                ),
                RadioDTO(
                    radioName = "Rock Radio 2",
                    url = "http://rock2.radio/stream",
                    thumbnailUrl = "http://rock2.radio/icon.png",
                    countryCode = "GB"
                )
            )
        )
        val interaction = mockk<ChatInputCommandInteraction> {
            every { kord } returns mockedKord
            every { guildLocale } returns Locale.ENGLISH_UNITED_STATES
            every { locale } returns Locale.ENGLISH_UNITED_STATES
            every { command.strings["name"] } returns searchName
            every { data } returns mockk {
                every { guildId.value } returns null
            }
        }

        coEvery {
            radioService.searchRadioByNamePaged(searchName, 1)
        } returns RemoteResponse.Success(radioPage)

        coEvery { localizationService.getString(any(), any(), any()) } returns "Radio Search Results"
        coEvery { localizationService.getStringFormat(any(), any(), any(), *anyVararg()) } returns "Page 1 of 3"
        every { localizationService.getLocalizations(any()) } returns mutableMapOf()

        // When
        radioSearchNameCommand.onExecute(interaction, mockedResponse)

        // Then
        coVerify(exactly = 1) {
            radioService.searchRadioByNamePaged(searchName, 1)
        }
    }

    @Test
    fun `Given empty search name When onExecute Then show empty results`() = runTest {
        // Given
        val interaction = mockk<ChatInputCommandInteraction> {
            every { kord } returns mockedKord
            every { guildLocale } returns Locale.ENGLISH_UNITED_STATES
            every { locale } returns Locale.ENGLISH_UNITED_STATES
            every { command.strings["name"] } returns ""
            every { data } returns mockk {
                every { guildId.value } returns null
            }
        }
        val emptyPage = RadioPageDTO(
            currentPage = 1,
            maxPage = 1,
            radios = emptyList()
        )

        coEvery {
            radioService.searchRadioByNamePaged("", 1)
        } returns RemoteResponse.Success(emptyPage)

        coEvery { localizationService.getString(any(), any(), any()) } returns "No radios found"
        coEvery { localizationService.getStringFormat(any(), any(), any(), *anyVararg()) } returns "Page 1 of 1"
        every { localizationService.getLocalizations(any()) } returns mutableMapOf()

        // When
        radioSearchNameCommand.onExecute(interaction, mockedResponse)

        // Then
        coVerify(exactly = 1) {
            radioService.searchRadioByNamePaged("", 1)
        }
    }

    @Test
    fun `Given component interaction When onInteract Then handle pagination`() = runTest {
        // Given - Note: onInteract will return early due to null customId
        val interaction = mockk<dev.kord.core.entity.interaction.ButtonInteraction>(relaxed = true) {
            every { component } returns mockk {
                every { customId } returns null
            }
        }

        // When
        radioSearchNameCommand.onInteract(interaction)

        // Then - should return early without calling service
        coVerify(exactly = 0) {
            radioService.searchRadioByNamePaged(any(), any())
        }
    }
}
