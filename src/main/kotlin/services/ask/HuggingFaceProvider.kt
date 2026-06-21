package es.wokis.services.ask

import es.wokis.services.config.ConfigService
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val HF_CHAT_URL = "https://router.huggingface.co/hf-inference/models"

class HuggingFaceProvider(
    private val httpClient: HttpClient,
    private val configService: ConfigService
) : AIProvider {

    override suspend fun ask(prompt: String, model: String): String {
        val token = configService.config.ask.apiToken
        val url = "$HF_CHAT_URL/$model/v1/chat/completions"
        val body = ChatCompletionRequest(
            model = model,
            messages = listOf(Message(role = "user", content = prompt)),
            maxTokens = 500
        )
        val response: HttpResponse = httpClient.post(url) {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(body))
        }
        val completion = response.body<ChatCompletionResponse>()
        return completion.choices.firstOrNull()?.message?.content
            ?: throw IllegalStateException("Empty response from HuggingFace API")
    }
}

@Serializable
data class ChatCompletionRequest(
    val model: String,
    val messages: List<Message>,
    @kotlinx.serialization.SerialName("max_tokens")
    val maxTokens: Int = 500
)

@Serializable
data class Message(
    val role: String,
    val content: String
)

@Serializable
data class ChatCompletionResponse(
    val choices: List<Choice>
)

@Serializable
data class Choice(
    val message: Message
)
