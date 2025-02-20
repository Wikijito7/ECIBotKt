package utils

import dev.kord.common.Locale
import es.wokis.utils.orDefaultLocale
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class LocaleExtensionsTest {

    @Test
    fun `Given locale When orDefaultLocale is called Then return current locale`() {
        // Given
        val expected = Locale.THAI

        // When
        val actual = expected.orDefaultLocale()

        // Then
        assertEquals(expected, actual)
    }

    @Test
    fun `Given null locale When orDefaultLocale is called Then return US English`() {
        // Given
        val locale: Locale? = null
        val expected = Locale.ENGLISH_UNITED_STATES

        // When
        val actual = locale.orDefaultLocale()

        // Then
        assertEquals(expected, actual)
    }
}
