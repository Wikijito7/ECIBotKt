import es.wokis.example.Example
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ExampleTest {

    @Test
    fun `Given number When add Then return correct value`() {
        // Given
        val expected = 5
        // When
        val actual = 3 + 2
        // Then
        assertEquals(actual, expected)
    }

    @Test
    fun `Given name When getCoolName is called Then return cool name`() {
        // Given
        val name = "Pepe"
        val example = Example(name)
        val expected = "Pepe :sunglasses:"

        // When
        val actual = example.getCoolName()

        assertEquals(actual, expected)
    }

    @Test
    fun `Given name When name is called Then return name`() {
        // Given
        val example = Example()

        // When
        val actual = example.name

        assertTrue(actual.isEmpty())
    }
}
