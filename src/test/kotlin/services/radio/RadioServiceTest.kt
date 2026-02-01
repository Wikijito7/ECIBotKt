package services.radio

import es.wokis.data.radio.RadioDTO
import es.wokis.data.response.RemoteResponse
import es.wokis.services.config.ConfigService
import es.wokis.services.radio.RadioService
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import mock.getMockedHttpClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RadioServiceTest {

    private val configService: ConfigService = mockk {
        every { config } returns mockk {
            every { debug } returns false
        }
    }

    @Test
    fun `Given valid response When getRandomRadio is called Then return radio`() = runTest {
        // Given
        val radioJson = """
            {
                "name": "Test Radio",
                "url": "http://test.radio/stream",
                "favicon": "http://test.radio/icon.png",
                "countrycode": "US"
            }
        """.trimIndent()

        val httpClient = getMockedHttpClient(radioJson)
        val radioService = RadioService(httpClient, configService)

        // When
        val result = radioService.getRandomRadio()

        // Then
        assertTrue(result is RemoteResponse.Success)
        val radio = (result as RemoteResponse.Success).data
        assertEquals("Test Radio", radio?.radioName)
        assertEquals("http://test.radio/stream", radio?.url)
        assertEquals("http://test.radio/icon.png", radio?.thumbnailUrl)
        assertEquals("US", radio?.countryCode)
    }

    @Test
    fun `Given error response When getRandomRadio is called Then return error`() = runTest {
        // Given
        val httpClient = getMockedHttpClient("{}")
        val radioService = RadioService(httpClient, configService)

        // When
        val result = radioService.getRandomRadio()

        // Then
        // Since the response is empty JSON and doesn't match RadioDTO structure,
        // it will fail to deserialize and return an error wrapped by ErrorManagementWrapper
        assertTrue(result is RemoteResponse.Error)
    }

    @Test
    fun `Given valid country codes list When getCountryCodes is called Then return list`() = runTest {
        // Given
        val countryCodesJson = """
            {"countryCode": ["US", "ES", "FR", "DE", "IT"]}
        """.trimIndent()

        val httpClient = getMockedHttpClient(countryCodesJson)
        val radioService = RadioService(httpClient, configService)

        // When
        val result = radioService.getCountryCodes()

        // Then
        assertTrue(result is RemoteResponse.Success)
        val codes = (result as RemoteResponse.Success).data?.countryCodes.orEmpty()
        println(codes)
        assertEquals(5, codes.size)
        assertEquals("US", codes[0])
        assertEquals("ES", codes[1])
        assertEquals("FR", codes[2])
    }

    @Test
    fun `Given empty country codes list When getCountryCodes is called Then return empty list`() = runTest {
        // Given
        val countryCodesJson = """{"countryCode": []}"""

        val httpClient = getMockedHttpClient(countryCodesJson)
        val radioService = RadioService(httpClient, configService)

        // When
        val result = radioService.getCountryCodes()
        println(result)

        // Then
        assertTrue(result is RemoteResponse.Success)
        val codes = (result as RemoteResponse.Success).data?.countryCodes.orEmpty()
        assertTrue(codes.isEmpty())
    }
}
