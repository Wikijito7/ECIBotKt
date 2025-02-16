package services.localization

import dev.kord.common.Locale
import es.wokis.services.localization.LocalizationService
import es.wokis.services.localization.NoLocalizationFoundException
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LocalizationServiceTest {

    private val localizationService = LocalizationService()

    @Test
    fun `Given key When getString is called Then return default string`() {
        // Given
        val key = "test_command_description"
        val expected = "test in junit tests"

        // When
        val actual = localizationService.getString(key)

        // Then
        assertEquals(expected, actual)
    }

    @Test
    fun `Given key When getString is called with locale Then return localized string`() {
        // Given
        val key = "test_command_description"
        val expected = "ke dise iyo"

        // When
        val actual = localizationService.getString(key, Locale.SPANISH_SPAIN)

        // Then
        assertEquals(expected, actual)
    }

    @Test
    fun `Given key When getString is called with locale without translation Then return default string`() {
        // Given
        val key = "test_command_description"
        val expected = "test in junit tests"

        // When
        val actual = localizationService.getString(key, Locale.FRENCH)

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
    fun `Given invalid key When getString is called Then throw exception`() {
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
    fun `Given invalid key When getString is called with a locale Then throw exception`() {
        // Given
        val key = "totally_unknown_key"

        try {
            // When
            localizationService.getString(key, Locale.BULGARIAN)
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
    fun `Given key When getStringFormat is called Then return formated string`() {
        // Given
        val key = "test_args_string"
        val result = "this string has arguments, yay!"

        // When
        val actual = localizationService.getStringFormat(key, arguments = arrayOf("yay!"))

        // Then
        assertEquals(result, actual)
    }

    @Test
    fun `Given key When getStringFormat is called with a known locale Then return formated string`() {
        // Given
        val key = "test_args_string"
        val result = "esta string tiene argumentos, yay!"

        // When
        val actual = localizationService.getStringFormat(key, Locale.SPANISH_SPAIN, arguments = arrayOf("yay!"))

        // Then
        assertEquals(result, actual)
    }

    @Test
    fun `Given key When getStringFormat is called with a unregistered locale Then return formated string`() {
        // Given
        val key = "test_args_string"
        val result = "this string has arguments, yay!"

        // When
        val actual = localizationService.getStringFormat(key, Locale.FRENCH, arguments = arrayOf("yay!"))

        // Then
        assertEquals(result, actual)
    }
}
