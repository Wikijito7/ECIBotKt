package utils

import es.wokis.utils.UrlTransformer
import es.wokis.utils.transformUrl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class UrlTransformerTest {

    @Test
    fun `Given monochrome samidy url When transformUrl called Then returns tidal url`() {
        // Given
        val input = "https://monochrome.samidy.com/track/97034820"
        val expected = "https://tidal.com/track/97034820"

        // When
        val result = input.transformUrl()

        // Then
        assertEquals(expected, result)
    }

    @Test
    fun `Given monochrome me url When transformUrl called Then returns tidal url`() {
        // Given
        val input = "https://monochrome.me/whatever"
        val expected = "https://tidal.com/whatever"

        // When
        val result = input.transformUrl()

        // Then
        assertEquals(expected, result)
    }

    @Test
    fun `Given monochrome url with album path When transformUrl called Then returns tidal url with album path`() {
        // Given
        val input = "https://monochrome.samidy.com/album/12345"
        val expected = "https://tidal.com/album/12345"

        // When
        val result = input.transformUrl()

        // Then
        assertEquals(expected, result)
    }

    @Test
    fun `Given monochrome url with http When transformUrl called Then returns tidal url with http`() {
        // Given
        val input = "http://monochrome.samidy.com/track/123"
        val expected = "http://tidal.com/track/123"

        // When
        val result = input.transformUrl()

        // Then
        assertEquals(expected, result)
    }

    @Test
    fun `Given monochrome url with uppercase When transformUrl called Then returns tidal url`() {
        // Given
        val input = "https://MONOCHROME.samidy.com/track/123"
        val expected = "https://tidal.com/track/123"

        // When
        val result = input.transformUrl()

        // Then
        assertEquals(expected, result)
    }

    @Test
    fun `Given non monochrome url When transformUrl called Then returns original url unchanged`() {
        // Given
        val input = "https://youtube.com/watch?v=123"
        val expected = "https://youtube.com/watch?v=123"

        // When
        val result = input.transformUrl()

        // Then
        assertEquals(expected, result)
    }

    @Test
    fun `Given tidal url When transformUrl called Then returns original url unchanged`() {
        // Given
        val input = "https://tidal.com/track/123"
        val expected = "https://tidal.com/track/123"

        // When
        val result = input.transformUrl()

        // Then
        assertEquals(expected, result)
    }

    @Test
    fun `Given url without protocol When transformUrl called Then adds https and transforms`() {
        // Given
        val input = "monochrome.samidy.com/track/123"
        val expected = "https://tidal.com/track/123"

        // When
        val result = input.transformUrl()

        // Then
        assertEquals(expected, result)
    }

    @Test
    fun `Given monochrome url with no path When transformUrl called Then returns tidal root`() {
        // Given
        val input = "https://monochrome.samidy.com"
        val expected = "https://tidal.com"

        // When
        val result = input.transformUrl()

        // Then
        assertEquals(expected, result)
    }

    @Test
    fun `Given empty string When transformUrl called Then returns empty string`() {
        // Given
        val input = ""
        val expected = ""

        // When
        val result = input.transformUrl()

        // Then
        assertEquals(expected, result)
    }

    @Test
    fun `Given monochrome url with query params When transformUrl called Then preserves query params`() {
        // Given
        val input = "https://monochrome.samidy.com/track/123?foo=bar"
        val expected = "https://tidal.com/track/123?foo=bar"

        // When
        val result = input.transformUrl()

        // Then
        assertEquals(expected, result)
    }
}
