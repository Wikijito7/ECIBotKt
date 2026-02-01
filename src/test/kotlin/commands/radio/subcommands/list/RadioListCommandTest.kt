package commands.radio.subcommands.list

import dev.kord.common.Locale
import dev.kord.core.entity.interaction.ChatInputCommandInteraction
import es.wokis.commands.radio.subcommands.list.RadioListCommand
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

class RadioListCommandTest {

    private val radioService: RadioService = mockk()
    private val localizationService: LocalizationService = mockk()

    private val radioListCommand = RadioListCommand(
        radioService = radioService,
        localizationService = localizationService
    )

    @Test
    fun `Given list command When onExecute Then show first page of radios`() = runTest {
        // Given
        val radioPage = RadioPageDTO(
            currentPage = 1,
            maxPage = 5,
            radios = listOf(
                RadioDTO(
                    radioName = "Radio 1",
                    url = "http://radio1.stream",
                    thumbnailUrl = "http://radio1.icon.png",
                    countryCode = "US"
                ),
                RadioDTO(
                    radioName = "Radio 2",
                    url = "http://radio2.stream",
                    thumbnailUrl = "http://radio2.icon.png",
                    countryCode = "GB"
                ),
                RadioDTO(
                    radioName = "Radio 3",
                    url = "http://radio3.stream",
                    thumbnailUrl = "http://radio3.icon.png",
                    countryCode = "ES"
                )
            )
        )
        val interaction = mockk<ChatInputCommandInteraction> {
            every { kord } returns mockedKord
            every { guildLocale } returns Locale.ENGLISH_UNITED_STATES
            every { locale } returns Locale.ENGLISH_UNITED_STATES
        }

        coEvery {
            radioService.getRadioList(1)
        } returns RemoteResponse.Success(radioPage)

        every { localizationService.getString(any(), any()) } returns "Radio List"
        every { localizationService.getStringFormat(any(), any(), *anyVararg()) } returns "Page 1 of 5"
        every { localizationService.getLocalizations(any()) } returns mutableMapOf()

        // When
        radioListCommand.onExecute(interaction, mockedResponse)

        // Then
        coVerify(exactly = 1) {
            radioService.getRadioList(1)
        }
    }

    @Test
    fun `Given empty radio list When onExecute Then show empty page`() = runTest {
        // Given
        val emptyPage = RadioPageDTO(
            currentPage = 1,
            maxPage = 1,
            radios = emptyList()
        )
        val interaction = mockk<ChatInputCommandInteraction> {
            every { kord } returns mockedKord
            every { guildLocale } returns Locale.ENGLISH_UNITED_STATES
            every { locale } returns Locale.ENGLISH_UNITED_STATES
        }

        coEvery {
            radioService.getRadioList(1)
        } returns RemoteResponse.Success(emptyPage)

        every { localizationService.getString(any(), any()) } returns "No radios available"
        every { localizationService.getStringFormat(any(), any(), *anyVararg()) } returns "Page 1 of 1"
        every { localizationService.getLocalizations(any()) } returns mutableMapOf()

        // When
        radioListCommand.onExecute(interaction, mockedResponse)

        // Then
        coVerify(exactly = 1) {
            radioService.getRadioList(1)
        }
    }

}
