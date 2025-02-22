package data.response

import es.wokis.data.response.ErrorType
import es.wokis.data.response.RemoteResponse
import es.wokis.data.response.map
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class RemoteResponseTest {

    @Test
    fun `Given remote response success When map is called Then assert response is mapped`() {
        // Given
        val response = RemoteResponse.Success("3")
        val expected = 3

        // When
        val actual = response.map { it.toIntOrNull() }

        // Then
        assertNotNull(actual.data)
        assertEquals(expected, actual.data)
    }

    @Test
    fun `Given remote response error When map is called Then assert response is mapped`() {
        // Given
        val response = RemoteResponse.Error(ErrorType.UnknownError(Exception(), ""), "3")
        val expected = 3

        // When
        val actual = response.map { it.toIntOrNull() }

        // Then
        assertNotNull(actual.data)
        assertEquals(expected, actual.data)
    }
}
