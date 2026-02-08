package utils

import es.wokis.utils.isValidUrl
import es.wokis.utils.takeIfNotEmpty
import es.wokis.utils.toSanitizedMarkdownLink
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

    @Test
    fun `Given text without emoji When toSanitizedMarkdownLink is called Then return simple markdown link`() {
        // Given
        val text = "Hello World"
        val url = "https://example.com"

        // When
        val actual = text.toSanitizedMarkdownLink(url)

        // Then
        assertEquals("[Hello World](https://example.com)", actual)
    }

    @Test
    fun `Given text with emoji at start When toSanitizedMarkdownLink is called Then emoji is not in link`() {
        // Given
        val text = "🎵 Music Track"
        val url = "https://example.com"

        // When
        val actual = text.toSanitizedMarkdownLink(url)

        // Then
        assertEquals("🎵[ Music Track](https://example.com)", actual)
    }

    @Test
    fun `Given text with emoji at end When toSanitizedMarkdownLink is called Then emoji is not in link`() {
        // Given
        val text = "Music Track 🎵"
        val url = "https://example.com"

        // When
        val actual = text.toSanitizedMarkdownLink(url)

        // Then
        assertEquals("[Music Track ](https://example.com)🎵", actual)
    }

    @Test
    fun `Given text with emoji in middle When toSanitizedMarkdownLink is called Then splits into two links`() {
        // Given
        val text = "Music 🎵 Track"
        val url = "https://example.com"

        // When
        val actual = text.toSanitizedMarkdownLink(url)

        // Then
        assertEquals("[Music ](https://example.com)🎵[ Track](https://example.com)", actual)
    }

    @Test
    fun `Given text with multiple emojis When toSanitizedMarkdownLink is called Then handles all emojis`() {
        // Given
        val text = "🎵 Music 🎶 Track 🔊"
        val url = "https://example.com"

        // When
        val actual = text.toSanitizedMarkdownLink(url)

        // Then
        assertEquals("🎵[ Music ](https://example.com)🎶[ Track ](https://example.com)🔊", actual)
    }

    @Test
    fun `Given only emojis When toSanitizedMarkdownLink is called Then returns emojis without links`() {
        // Given
        val text = "🎵🎶🔊"
        val url = "https://example.com"

        // When
        val actual = text.toSanitizedMarkdownLink(url)

        // Then
        assertEquals("🎵🎶🔊", actual)
    }

    @Test
    fun `Given empty string When toSanitizedMarkdownLink is called Then return empty string`() {
        // Given
        val text = ""
        val url = "https://example.com"

        // When
        val actual = text.toSanitizedMarkdownLink(url)

        // Then
        assertEquals("", actual)
    }

    @Test
    fun `Given text with ZWJ sequence emoji When toSanitizedMarkdownLink is called Then handles combined emoji as one`() {
        // Given - 👨🏻‍💻 (Man Technologist: Light Skin Tone) is a ZWJ sequence
        val text = "Dev 👨🏻‍💻 Coding"
        val url = "https://example.com"

        // When
        val actual = text.toSanitizedMarkdownLink(url)

        // Then - The entire ZWJ sequence should be treated as one emoji
        assertEquals("[Dev ](https://example.com)👨🏻‍💻[ Coding](https://example.com)", actual)
    }

    @Test
    fun `Given text with skin tone modifier When toSanitizedMarkdownLink is called Then handles as one emoji`() {
        // Given - 👋🏽 (Waving Hand: Medium Skin Tone)
        val text = "Hello 👋🏽 World"
        val url = "https://example.com"

        // When
        val actual = text.toSanitizedMarkdownLink(url)

        // Then - Base emoji + skin tone should be treated as one emoji
        assertEquals("[Hello ](https://example.com)👋🏽[ World](https://example.com)", actual)
    }

    @Test
    fun `Given text with flag emoji When toSanitizedMarkdownLink is called Then handles flag as one emoji`() {
        // Given - 🇺🇸 (US Flag) is two regional indicator symbols
        val text = "USA 🇺🇸 America"
        val url = "https://example.com"

        // When
        val actual = text.toSanitizedMarkdownLink(url)

        // Then - Flag should be treated as one emoji
        assertEquals("[USA ](https://example.com)🇺🇸[ America](https://example.com)", actual)
    }
}
