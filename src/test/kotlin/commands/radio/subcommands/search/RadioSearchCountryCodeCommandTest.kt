package commands.radio.subcommands.search

import dev.kord.common.Locale
import dev.kord.common.entity.Snowflake
import dev.kord.core.entity.interaction.AutoCompleteInteraction
import dev.kord.core.entity.interaction.ChatInputCommandInteraction
import es.wokis.commands.radio.subcommands.search.RadioSearchCountryCodeCommand
import es.wokis.data.radio.RadioCountryCodeDTO
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

class RadioSearchCountryCodeCommandTest {

    private val radioService: RadioService = mockk()
    private val localizationService: LocalizationService = mockk()

    private val radioSearchCountryCodeCommand = RadioSearchCountryCodeCommand(
        radioService = radioService,
        localizationService = localizationService
    )

    @Test
    fun `Given country code When onExecute Then show paginated results`() = runTest {
        // Given
        val countryCode = "US"
        val radioPage = RadioPageDTO(
            currentPage = 1,
            maxPage = 5,
            radios = listOf(
                RadioDTO(
                    radioName = "US Radio 1",
                    url = "http://us1.radio/stream",
                    thumbnailUrl = "http://us1.radio/icon.png",
                    countryCode = "US"
                ),
                RadioDTO(
                    radioName = "US Radio 2",
                    url = "http://us2.radio/stream",
                    thumbnailUrl = "http://us2.radio/icon.png",
                    countryCode = "US"
                )
            )
        )
        val interaction = mockk<ChatInputCommandInteraction> {
            every { kord } returns mockedKord
            every { guildLocale } returns Locale.ENGLISH_UNITED_STATES
            every { locale } returns Locale.ENGLISH_UNITED_STATES
            every { command.strings["countrycode"] } returns countryCode
        }

        coEvery {
            radioService.searchRadioByCountryCodePaged(countryCode, 1)
        } returns RemoteResponse.Success(radioPage)

        every { localizationService.getString(any(), any()) } returns "Radio List"
        every { localizationService.getStringFormat(any(), any(), *anyVararg()) } returns "Page 1 of 5"
        every { localizationService.getLocalizations(any()) } returns mutableMapOf()

        // When
        radioSearchCountryCodeCommand.onExecute(interaction, mockedResponse)

        // Then
        coVerify(exactly = 1) {
            radioService.searchRadioByCountryCodePaged(countryCode, 1)
        }
    }

    @Test
    fun `Given empty country code When onExecute Then show empty results`() = runTest {
        // Given
        val interaction = mockk<ChatInputCommandInteraction> {
            every { kord } returns mockedKord
            every { guildLocale } returns Locale.ENGLISH_UNITED_STATES
            every { locale } returns Locale.ENGLISH_UNITED_STATES
            every { command.strings["countrycode"] } returns ""
        }
        val emptyPage = RadioPageDTO(
            currentPage = 1,
            maxPage = 1,
            radios = emptyList()
        )

        coEvery {
            radioService.searchRadioByCountryCodePaged("", 1)
        } returns RemoteResponse.Success(emptyPage)

        every { localizationService.getString(any(), any()) } returns "No radios found"
        every { localizationService.getStringFormat(any(), any(), *anyVararg()) } returns "Page 1 of 1"
        every { localizationService.getLocalizations(any()) } returns mutableMapOf()

        // When
        radioSearchCountryCodeCommand.onExecute(interaction, mockedResponse)

        // Then
        coVerify(exactly = 1) {
            radioService.searchRadioByCountryCodePaged("", 1)
        }
    }

    @Test
    fun `Given autocomplete with partial code When onAutoComplete Then return matching codes`() = runTest {
        // Given
        val input = "U"
        val countryCodes = RadioCountryCodeDTO(
            countryCodes = listOf("US", "GB", "FR", "DE", "IT", "ES", "AU")
        )
        val interaction = mockk<AutoCompleteInteraction>(relaxed = true) {
            every { kord } returns mockedKord
            every { id } returns Snowflake(123456789)
            every { token } returns "test-token"
            every { command.strings["countrycode"] } returns input
        }

        coEvery {
            radioService.getCountryCodes()
        } returns RemoteResponse.Success(countryCodes)

        // When
        radioSearchCountryCodeCommand.onAutoComplete(interaction)

        // Then
        coVerify(exactly = 1) {
            radioService.getCountryCodes()
        }
    }

    @Test
    fun `Given autocomplete with no matches When onAutoComplete Then return empty list`() = runTest {
        // Given
        val input = "XYZ"
        val countryCodes = RadioCountryCodeDTO(
            countryCodes = listOf("US", "GB", "FR", "DE", "IT")
        )
        val interaction = mockk<AutoCompleteInteraction>(relaxed = true) {
            every { kord } returns mockedKord
            every { id } returns Snowflake(123456789)
            every { token } returns "test-token"
            every { command.strings["countrycode"] } returns input
        }

        coEvery {
            radioService.getCountryCodes()
        } returns RemoteResponse.Success(countryCodes)

        // When
        radioSearchCountryCodeCommand.onAutoComplete(interaction)

        // Then
        coVerify(exactly = 1) {
            radioService.getCountryCodes()
        }
    }

    @Test
    fun `Given autocomplete with empty input When onAutoComplete Then return empty list`() = runTest {
        // Given
        val interaction = mockk<AutoCompleteInteraction>(relaxed = true) {
            every { kord } returns mockedKord
            every { id } returns Snowflake(123456789)
            every { token } returns "test-token"
            every { command.strings["countrycode"] } returns ""
        }

        // When
        radioSearchCountryCodeCommand.onAutoComplete(interaction)

        // Then
        coVerify(exactly = 0) {
            radioService.getCountryCodes()
        }
    }
}
