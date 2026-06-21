package services.localization

import dev.kord.common.Locale
import dev.kord.common.entity.Snowflake
import es.wokis.domain.locale.GetGuildLocaleUseCase
import es.wokis.services.localization.LocalizationService
import es.wokis.services.localization.NoLocalizationFoundException
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LocalizationServiceTest {

    private val getGuildLocaleUseCase: GetGuildLocaleUseCase = mockk()
    private val localizationService = LocalizationService(getGuildLocaleUseCase)

    @Test
    fun `Given key When getString is called Then return default string`() = runTest {
        // Given
        val key = "test_command_description"
        val expected = "test in junit tests"

        // When
        val actual = localizationService.getString(key)

        // Then
        assertEquals(expected, actual)
    }

    @Test
    fun `Given key When getString is called with discordLocale Then return localized string`() = runTest {
        // Given
        val key = "test_command_description"
        val expected = "ke dise iyo"

        // When
        val actual = localizationService.getString(key, discordLocale = Locale.SPANISH_SPAIN)

        // Then
        assertEquals(expected, actual)
    }

    @Test
    fun `Given key When getString is called with guildId that has custom locale Then return localized string`() = runTest {
        // Given
        val key = "test_command_description"
        val guildId = Snowflake(123)
        val expected = "ke dise iyo"
        coEvery { getGuildLocaleUseCase(guildId) } returns Locale.SPANISH_SPAIN

        // When
        val actual = localizationService.getString(key, guildId = guildId)

        // Then
        assertEquals(expected, actual)
    }

    @Test
    fun `Given key When getString is called with discordLocale without translation Then return default string`() = runTest {
        // Given
        val key = "test_command_description"
        val expected = "test in junit tests"

        // When
        val actual = localizationService.getString(key, discordLocale = Locale.FRENCH)

        // Then
        assertEquals(expected, actual)
    }

    @Test
    fun `Given key When getLocalizations is called Then return all localized string`() {
        // Given
        val key = "test_command_description"

        // When
        val actual = localizationService.getLocalizations(key)

        // Then
        assertEquals(actual.keys.size, 2)
    }

    @Test
    fun `Given invalid key When getString is called Then throw exception`() = runTest {
        // Given
        val key = "totally_unknown_key"

        try {
            // When
            localizationService.getString(key)
        } catch (exception: Exception) {
            // Then
            assertTrue(exception is NoLocalizationFoundException)
        }
    }

    @Test
    fun `Given invalid key When getString is called with a discordLocale Then throw exception`() = runTest {
        // Given
        val key = "totally_unknown_key"

        try {
            // When
            localizationService.getString(key, discordLocale = Locale.BULGARIAN)
        } catch (exception: Exception) {
            // Then
            assertTrue(exception is NoLocalizationFoundException)
        }
    }

    @Test
    fun `Given invalid key When getLocalizations is called Then throw exception`() {
        // Given
        val key = "totally_unknown_key"

        try {
            // When
            localizationService.getLocalizations(key)
        } catch (exception: Exception) {
            // Then
            assertTrue(exception is NoLocalizationFoundException)
        }
    }

    @Test
    fun `Given key When getStringFormat is called Then return formated string`() = runTest {
        // Given
        val key = "test_args_string"
        val result = "this string has arguments, yay!"

        // When
        val actual = localizationService.getStringFormat(key, arguments = arrayOf("yay!"))

        // Then
        assertEquals(result, actual)
    }

    @Test
    fun `Given key When getStringFormat is called with a known discordLocale Then return formated string`() = runTest {
        // Given
        val key = "test_args_string"
        val result = "esta string tiene argumentos, yay!"

        // When
        val actual = localizationService.getStringFormat(
            key,
            discordLocale = Locale.SPANISH_SPAIN,
            arguments = arrayOf("yay!")
        )

        // Then
        assertEquals(result, actual)
    }

    @Test
    fun `Given key When getStringFormat is called with a guildId that has custom locale Then return formated string`() = runTest {
        // Given
        val key = "test_args_string"
        val guildId = Snowflake(123)
        val result = "esta string tiene argumentos, yay!"
        coEvery { getGuildLocaleUseCase(guildId) } returns Locale.SPANISH_SPAIN

        // When
        val actual = localizationService.getStringFormat(key, guildId = guildId, arguments = arrayOf("yay!"))

        // Then
        assertEquals(result, actual)
    }

    @Test
    fun `Given key When getStringFormat is called with a unregistered discordLocale Then return formated string`() = runTest {
        // Given
        val key = "test_args_string"
        val result = "this string has arguments, yay!"

        // When
        val actual = localizationService.getStringFormat(
            key,
            discordLocale = Locale.FRENCH,
            arguments = arrayOf("yay!")
        )

        // Then
        assertEquals(result, actual)
    }
}
