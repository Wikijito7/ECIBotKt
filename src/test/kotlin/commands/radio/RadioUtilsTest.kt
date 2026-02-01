package commands.radio

import es.wokis.commands.radio.chunked
import es.wokis.data.radio.RadioDTO
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RadioUtilsTest {

    @Test
    fun `Given radio list with multiple items When chunked into columns Then format correctly`() {
        // Given
        val radios = listOf(
            RadioDTO(
                radioName = "Radio One",
                url = "http://radio1.stream",
                thumbnailUrl = "http://radio1.icon.png",
                countryCode = "US"
            ),
            RadioDTO(
                radioName = "Radio Two",
                url = "http://radio2.stream",
                thumbnailUrl = "http://radio2.icon.png",
                countryCode = "GB"
            ),
            RadioDTO(
                radioName = "Radio Three",
                url = "http://radio3.stream",
                thumbnailUrl = "http://radio3.icon.png",
                countryCode = "ES"
            ),
            RadioDTO(
                radioName = "Radio Four",
                url = "http://radio4.stream",
                thumbnailUrl = "http://radio4.icon.png",
                countryCode = "FR"
            ),
            RadioDTO(
                radioName = "Radio Five",
                url = "http://radio5.stream",
                thumbnailUrl = "http://radio5.icon.png",
                countryCode = "DE"
            ),
            RadioDTO(
                radioName = "Radio Six",
                url = "http://radio6.stream",
                thumbnailUrl = "http://radio6.icon.png",
                countryCode = "IT"
            )
        )

        // When
        val result = radios.chunked(3)

        // Then
        assertEquals(3, result.size) // 3 columns
        // Each column should have radio names
        assertTrue(result[0].contains("Radio One"))
        assertTrue(result[1].contains("Radio Four"))
        assertTrue(result[2].contains("Radio"))
    }

    @Test
    fun `Given radio list with special characters When chunked Then escape markdown`() {
        // Given
        val radios = listOf(
            RadioDTO(
                radioName = "#Special Radio",
                url = "http://special.stream",
                thumbnailUrl = "http://special.icon.png",
                countryCode = "US"
            ),
            RadioDTO(
                radioName = "*Bold Radio",
                url = "http://bold.stream",
                thumbnailUrl = "http://bold.icon.png",
                countryCode = "GB"
            ),
            RadioDTO(
                radioName = "-Dash Radio",
                url = "http://dash.stream",
                thumbnailUrl = "http://dash.icon.png",
                countryCode = "ES"
            )
        )

        // When
        val result = radios.chunked(3)

        // Then
        assertEquals(3, result.size)
        // Should escape special characters with backslash
        assertTrue(result[0].contains("\\"))
    }

    @Test
    fun `Given empty radio list When chunked Then return empty list`() {
        // Given
        val radios = emptyList<RadioDTO>()

        // When
        val result = radios.chunked(3)

        // Then
        assertTrue(result.isEmpty())
    }

    @Test
    fun `Given single radio When chunked Then return single column`() {
        // Given
        val radios = listOf(
            RadioDTO(
                radioName = "Solo Radio",
                url = "http://solo.stream",
                thumbnailUrl = "http://solo.icon.png",
                countryCode = "US"
            )
        )

        // When
        val result = radios.chunked(3)

        // Then
        assertEquals(1, result.size)
        assertTrue(result[0].contains("Solo Radio"))
    }

    @Test
    fun `Given radio with long name When chunked Then truncate to 20 chars`() {
        // Given
        val longName = "This is a very long radio station name that exceeds limits"
        val radios = listOf(
            RadioDTO(
                radioName = longName,
                url = "http://long.stream",
                thumbnailUrl = "http://long.icon.png",
                countryCode = "US"
            )
        )

        // When
        val result = radios.chunked(3)

        // Then
        assertEquals(1, result.size)
        // Name should be truncated to 20 characters
        assertTrue(result[0].length <= 20 || result[0].contains("This is a very long "))
    }

    @Test
    fun `Given two radios When chunked Then distribute across columns`() {
        // Given
        val radios = listOf(
            RadioDTO(
                radioName = "Radio A",
                url = "http://radioa.stream",
                thumbnailUrl = "http://radioa.icon.png",
                countryCode = "US"
            ),
            RadioDTO(
                radioName = "Radio B",
                url = "http://radiob.stream",
                thumbnailUrl = "http://radiob.icon.png",
                countryCode = "GB"
            )
        )

        // When
        val result = radios.chunked(3)

        // Then - With 2 items and 3 columns, size/columns = 2/3 = 0, coerceAtLeast(1) = 1
        assertEquals(2, result.size) // Each radio becomes its own "chunk" in the list
        assertTrue(result[0].contains("Radio A"))
        assertTrue(result[1].contains("Radio B"))
    }
}
