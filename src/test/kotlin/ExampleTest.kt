import es.wokis.example.Example
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
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
        val example = mockk<Example>()
        val name = "Pepe"
        val result = "Pepo"
        every { example.name } returns name
        every { example.getCoolName() } returns result

        // When
        val actual = example.getCoolName()

        // Then
        verify(exactly = 1) {
            example.getCoolName()
        }

        assertEquals(actual, result)
    }
}
