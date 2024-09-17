package utils

import es.wokis.utils.asRegex
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class RegexUtilsTest {

    @Test
    fun `Given list When asRegex is called Then return a valid regex`() {
        // Given
        val strings = listOf("list", "of", "strings")
        val expected = Regex("(list|of|strings)")

        // When
        val actual = strings.asRegex()

        // Then
        assertEquals(expected.pattern, actual.pattern)
    }
}
