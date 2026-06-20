package services.ask

import es.wokis.services.ask.HuggingFaceProvider
import es.wokis.services.config.Config
import es.wokis.services.config.ConfigService
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mock.getMockedHttpClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class HuggingFaceProviderTest {

    private val configService: ConfigService = mockk {
        every { config } returns mockk {
            every { ask } returns mockk {
                every { apiToken } returns "test-token"
            }
        }
    }

    @Test
    fun `Given valid response When ask is called Then return answer`() = runTest {
        // Given
        val responseJson = Json.encodeToString(
            mapOf(
                "choices" to listOf(
                    mapOf(
                        "message" to mapOf(
                            "role" to "assistant",
                            "content" to "Hello, monkey!"
                        )
                    )
                )
            )
        )
        val httpClient = getMockedHttpClient(responseJson)
        val provider = HuggingFaceProvider(httpClient, configService)

        // When
        val result = provider.ask("Say hi", "test-model")

        // Then
        assertEquals("Hello, monkey!", result)
    }

    @Test
    fun `Given response with no choices When ask is called Then throw`() = runTest {
        // Given
        val responseJson = """{"choices": []}"""
        val httpClient = getMockedHttpClient(responseJson)
        val provider = HuggingFaceProvider(httpClient, configService)

        // When / Then
        assertThrows<IllegalStateException> {
            provider.ask("test", "test-model")
        }
    }

    @Test
    fun `Given error response When ask is called Then throw`() = runTest {
        // Given
        val httpClient = getMockedHttpClient("not json")
        val provider = HuggingFaceProvider(httpClient, configService)

        // When / Then
        assertThrows<Exception> {
            provider.ask("test", "test-model")
        }
    }
}
