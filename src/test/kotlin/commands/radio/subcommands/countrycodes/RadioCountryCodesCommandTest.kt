package commands.radio.subcommands.countrycodes

import dev.kord.common.Locale
import dev.kord.core.entity.interaction.ChatInputCommandInteraction
import es.wokis.commands.radio.subcommands.countrycodes.RadioCountryCodesCommand
import es.wokis.data.radio.RadioCountryCodeDTO
import es.wokis.data.response.ErrorType
import es.wokis.data.response.RemoteResponse
import es.wokis.services.localization.LocalizationService
import es.wokis.services.radio.RadioService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import mock.mockedKord
import mock.mockedResponse
import org.junit.jupiter.api.Test

class RadioCountryCodesCommandTest {

    private val radioService: RadioService = mockk()
    private val localizationService: LocalizationService = mockk()

    private val radioCountryCodesCommand = RadioCountryCodesCommand(
        radioService = radioService,
        localizationService = localizationService
    )

    @Test
    fun `Given country codes list When onExecute Then show formatted list`() = runTest {
        // Given
        val countryCodes = RadioCountryCodeDTO(countryCodes = listOf("US", "ES", "FR", "DE", "IT"))
        val interaction = mockk<ChatInputCommandInteraction> {
            every { kord } returns mockedKord
            every { guildLocale } returns Locale.ENGLISH_UNITED_STATES
            every { data } returns mockk {
                every { guildId.value } returns null
            }
        }

        coEvery {
            radioService.getCountryCodes()
        } returns RemoteResponse.Success(countryCodes)

        coEvery { localizationService.getString(any(), any(), any()) } returns "Available Country Codes"
        coEvery { localizationService.getStringFormat(any(), any(), any(), *anyVararg()) } returns "Page 1 of 1"

        // When
        radioCountryCodesCommand.onExecute(interaction, mockedResponse)

        // Then
        coVerify(exactly = 1) {
            radioService.getCountryCodes()
        }
    }

    @Test
    fun `Given empty country codes list When onExecute Then show empty message`() = runTest {
        // Given
        val interaction = mockk<ChatInputCommandInteraction> {
            every { kord } returns mockedKord
            every { guildLocale } returns Locale.ENGLISH_UNITED_STATES
            every { data } returns mockk {
                every { guildId.value } returns null
            }
        }

        coEvery {
            radioService.getCountryCodes()
        } returns RemoteResponse.Success(RadioCountryCodeDTO(countryCodes = emptyList()))

        coEvery { localizationService.getString(any(), any(), any()) } returns "No country codes available"
        coEvery { localizationService.getStringFormat(any(), any(), any(), *anyVararg()) } returns "Available country codes: "

        // When
        radioCountryCodesCommand.onExecute(interaction, mockedResponse)

        // Then
        coVerify(exactly = 1) {
            radioService.getCountryCodes()
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

        coEvery {
            radioService.getCountryCodes()
        } returns RemoteResponse.Error(ErrorType.UnknownError(Exception("API Error"), "API Error"))

        coEvery { localizationService.getString(any(), any(), any()) } returns "Error retrieving country codes"

        // When
        radioCountryCodesCommand.onExecute(interaction, mockedResponse)

        // Then
        coVerify(exactly = 1) {
            radioService.getCountryCodes()
        }
    }
}
