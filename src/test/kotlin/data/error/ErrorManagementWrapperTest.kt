package data.error

import es.wokis.data.error.ErrorManagementWrapper
import es.wokis.data.response.ErrorType
import es.wokis.data.response.RemoteResponse
import io.ktor.client.plugins.*
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.IllegalFormatException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ErrorManagementWrapperTest {

    @Test
    fun `Given cancellation exception When wrap is called Then verify exception is thrown`() = runTest {
        try {
            // When
            ErrorManagementWrapper.wrap {
                throw CancellationException()
            }
        } catch (exc: Exception) {
            // Then
            assertTrue(exc is CancellationException)
        }
    }

    @Test
    fun `Given request When wrap is called Then verify last emit is success`() = runTest {
        // Given
        val expected = "manolete"

        // When
        val result = ErrorManagementWrapper.wrap {
            expected
        }

        // Then
        assertNotNull(result)
        assertTrue(result is RemoteResponse.Success)
        assertEquals(result.data, expected)
    }

    @Test
    fun `Given request When wrap is called with error request Then verify last emit is unknown error`() = runTest {
        // Given
        val error = IllegalStateException()

        // When
        val result = ErrorManagementWrapper.wrap {
            throw error
        }

        // Then
        assertNotNull(result)
        assertTrue(result is RemoteResponse.Error)
        assertTrue(result.error is ErrorType.UnknownError)
    }

    @Test
    fun `Given request When wrap is called with redirect request Then verify last emit is server error`() = runTest {
        // Given
        val error = RedirectResponseException(
            response = mockk(relaxed = true),
            cachedResponseText = "Manolete"
        )

        // When
        val result = ErrorManagementWrapper.wrap {
            throw error
        }

        // Then
        assertNotNull(result)
        assertTrue(result is RemoteResponse.Error)
        assertTrue(result.error is ErrorType.ServerError)
    }

    @Test
    fun `Given request When wrap is called with erroneous request Then verify last emit is request error`() = runTest {
        // Given
        val error = ClientRequestException(
            response = mockk(relaxed = true),
            cachedResponseText = "Manolete"
        )

        // When
        val result = ErrorManagementWrapper.wrap {
            throw error
        }

        // Then
        assertNotNull(result)
        assertTrue(result is RemoteResponse.Error)
        assertTrue(result.error is ErrorType.RequestError)
    }

    @Test
    fun `Given request When wrap is called with server error response Then verify last emit is server error`() = runTest {
        // Given
        val error = ServerResponseException(
            response = mockk(relaxed = true),
            cachedResponseText = "Manolete"
        )

        // When
        val result = ErrorManagementWrapper.wrap {
            throw error
        }

        // Then
        assertNotNull(result)
        assertTrue(result is RemoteResponse.Error)
        assertTrue(result.error is ErrorType.ServerError)
    }

    @Test
    fun `Given request When wrap is called without internet Then verify last emit is no connection error`() = runTest {
        // Given
        val error = ConnectException()

        // When
        val result = ErrorManagementWrapper.wrap {
            throw error
        }

        // Then
        assertNotNull(result)
        assertTrue(result is RemoteResponse.Error)
        assertTrue(result.error is ErrorType.NoConnectionError)
    }

    @Test
    fun `Given request When wrap is called and timeout Then verify last emit is no connection error`() = runTest {
        // Given
        val error = SocketTimeoutException()

        // When
        val result = ErrorManagementWrapper.wrap {
            throw error
        }

        // Then
        assertNotNull(result)
        assertTrue(result is RemoteResponse.Error)
        assertTrue(result.error is ErrorType.NoConnectionError)
    }

    @Test
    fun `Given request to unknown host When wrap is called Then verify last emit is no connection error`() = runTest {
        // Given
        val error = UnknownHostException()

        // When
        val result = ErrorManagementWrapper.wrap {
            throw error
        }

        // Then
        assertNotNull(result)
        assertTrue(result is RemoteResponse.Error)
        assertTrue(result.error is ErrorType.NoConnectionError)
    }

    @Test
    fun `Given request with wrong response type When wrap is called Then verify last emit is data parse error`() = runTest {
        // Given
        val error: IllegalFormatException = mockk()

        // When
        val result = ErrorManagementWrapper.wrap {
            throw error
        }

        // Then
        assertNotNull(result)
        assertTrue(result is RemoteResponse.Error)
        assertTrue(result.error is ErrorType.DataParseError)
    }
}
