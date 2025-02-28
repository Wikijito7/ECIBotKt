package domain

import es.wokis.domain.GetFloweryVoicesUseCase
import kotlinx.coroutines.test.runTest
import mock.getMockedHttpClient
import kotlin.test.Test
import kotlin.test.assertTrue


class GetFloweryVoicesUseCaseTest {
    private val mockedResponse = """
                {
                    "voices": [
                        {
                            "language": {
                                "code": "%s"
                            },
                            "id": "%s"
                        }
                    ]
                }
            """.trimIndent()

    @Test
    fun `Given request with spanish voice When getFloweryVoicesUseCase is executed Then return spanish voices`() = runTest {
        // Given
        val getFloweryVoicesUseCase = getUseCase(mockedResponse.format("es-ES", "mi-totally-not-invented-id"))

        // When
        val result = getFloweryVoicesUseCase()

        // Then
        assertTrue(result.isNotEmpty())
        assertTrue(result.first() == "mi-totally-not-invented-id")
    }

    @Test
    fun `Given request with non spanish voice When getFloweryVoicesUseCase is executed Then return empty list`() = runTest {
        // Given
        val getFloweryVoicesUseCase = getUseCase(mockedResponse.format("en-US", "mi-totally-not-invented-id"))

        // When
        val result = getFloweryVoicesUseCase()

        // Then
        assertTrue(result.isEmpty())
    }

    @Test
    fun `Given request with invalid response When getFloweryVoicesUseCase is executed Then return empty list`() = runTest {
        // Given
        val getFloweryVoicesUseCase = getUseCase("invalid response")

        // When
        val result = getFloweryVoicesUseCase()

        // Then
        assertTrue(result.isEmpty())
    }

    private fun getUseCase(response: String): GetFloweryVoicesUseCase {
        val httpClient = getMockedHttpClient(response)
        return GetFloweryVoicesUseCase(httpClient = httpClient)
    }
}
