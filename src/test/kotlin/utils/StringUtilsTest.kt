package utils

import es.wokis.utils.isValidUrl
import es.wokis.utils.takeIfNotEmpty
import org.junit.jupiter.api.Test
import kotlin.test.*

class StringUtilsTest {

    @Test
    fun `Given string When takeIfNotEmpty is called Then return the string`() {
        // Given
        val expected = "manolete"

        // When
        val actual = expected.takeIfNotEmpty()

        // Then
        assertNotNull(actual)
        assertEquals(expected, actual)
    }

    @Test
    fun `Given empty string When takeIfNotEmpty is called Then return null`() {
        // Given
        val expected = ""

        // When
        val actual = expected.takeIfNotEmpty()

        // Then
        assertNull(actual)
    }

    @Test
    fun `Given https string When isValidUrl is called Then return true`() {
        // Given
        val expected = "https://totally.real/url"

        // When
        val actual = expected.isValidUrl()

        // Then
        assertTrue(actual)
    }

    @Test
    fun `Given http string When isValidUrl is called Then return true`() {
        // Given
        val expected = "http://totally.real/url"

        // When
        val actual = expected.isValidUrl()

        // Then
        assertTrue(actual)
    }

    @Test
    fun `Given string When isValidUrl is called Then return false`() {
        // Given
        val expected = "pepe"

        // When
        val actual = expected.isValidUrl()

        // Then
        assertFalse(actual)
    }
}
