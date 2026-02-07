package data.response

import es.wokis.data.response.ErrorType
import es.wokis.data.response.RemoteResponse
import es.wokis.data.response.getOrDefault
import es.wokis.data.response.getOrNull
import es.wokis.data.response.getOrThrow
import es.wokis.exceptions.BotException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RemoteResponseExtensionsTest {

    @Test
    fun `Given success response with data When getOrThrow is called Then return data`() {
        // Given
        val expectedData = "test data"
        val response = RemoteResponse.Success(expectedData)

        // When
        val actual = response.getOrThrow()

        // Then
        assertEquals(expectedData, actual)
    }

    @Test
    fun `Given success response with null data When getOrThrow is called Then throw IllegalStateException`() {
        // Given
        val response = RemoteResponse.Success<String>(null)

        // When/Then
        assertThrows<IllegalStateException> {
            response.getOrThrow()
        }
    }

    @Test
    fun `Given error response When getOrThrow is called Then throw APIException`() {
        // Given
        val error = ErrorType.UnknownError(Exception("test error"), "error message")
        val response = RemoteResponse.Error<String>(error, "test data")

        // When/Then
        assertThrows<BotException.APIException> {
            response.getOrThrow()
        }
    }

    @Test
    fun `Given success response with data When getOrNull is called Then return data`() {
        // Given
        val expectedData = "test data"
        val response = RemoteResponse.Success(expectedData)

        // When
        val actual = response.getOrNull()

        // Then
        assertEquals(expectedData, actual)
    }

    @Test
    fun `Given success response with null data When getOrNull is called Then return null`() {
        // Given
        val response = RemoteResponse.Success<String>(null)

        // When
        val actual = response.getOrNull()

        // Then
        assertNull(actual)
    }

    @Test
    fun `Given error response When getOrNull is called Then return null`() {
        // Given
        val error = ErrorType.UnknownError(Exception("test error"), "error message")
        val response = RemoteResponse.Error<String>(error, "test data")

        // When
        val actual = response.getOrNull()

        // Then
        assertNull(actual)
    }

    @Test
    fun `Given success response with data When getOrDefault is called Then return data`() {
        // Given
        val expectedData = "test data"
        val defaultValue = "default"
        val response = RemoteResponse.Success(expectedData)

        // When
        val actual = response.getOrDefault(defaultValue)

        // Then
        assertEquals(expectedData, actual)
    }

    @Test
    fun `Given success response with null data When getOrDefault is called Then return default`() {
        // Given
        val defaultValue = "default"
        val response = RemoteResponse.Success<String>(null)

        // When
        val actual = response.getOrDefault(defaultValue)

        // Then
        assertEquals(defaultValue, actual)
    }

    @Test
    fun `Given error response When getOrDefault is called Then return default`() {
        // Given
        val defaultValue = "default"
        val error = ErrorType.UnknownError(Exception("test error"), "error message")
        val response = RemoteResponse.Error<String>(error, "test data")

        // When
        val actual = response.getOrDefault(defaultValue)

        // Then
        assertEquals(defaultValue, actual)
    }
}
