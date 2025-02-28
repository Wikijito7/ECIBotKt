package mock

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.headers
import io.ktor.serialization.kotlinx.json.json

fun getMockedHttpClient(responseContent: String): HttpClient {
    val mockEngine = MockEngine {
        respond(
            content = responseContent,
            headers = headers {
                append("Content-Type", "application/json")
            }
        )
    }
    return HttpClient(mockEngine) {
        install(ContentNegotiation) {
            json()
        }
    }
}
