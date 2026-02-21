package es.wokis.services.assistant

import es.wokis.services.config.ConfigService
import es.wokis.utils.Log
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonIgnoreUnknownKeys

class OllamaService(
    private val httpClient: HttpClient,
    private val configService: ConfigService
) {
    suspend fun generateResponse(prompt: String, history: List<ConversationMessage> = emptyList()): String? {
        val config = configService.config.ollama
        if (!config.enabled) {
            Log.warning("Ollama is not enabled in config")
            return null
        }

        return try {
            val messages = mutableListOf(
                Message(role = "system", content = "You are a helpful voice assistant. Respond briefly and concisely to user messages.")
            )
            
            history.forEach { msg ->
                messages.add(Message(role = msg.role, content = msg.content))
            }
            messages.add(Message(role = "user", content = prompt))

            val request = OllamaChatRequest(
                model = config.model,
                messages = messages,
                stream = false
            )

            val response: OllamaChatResponse = httpClient.post("${config.baseUrl}/api/chat") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }.body()

            response.message?.content
        } catch (e: Exception) {
            Log.error("Error generating response from Ollama", exception = e)
            null
        }
    }
}

@Serializable
data class OllamaGenerateRequest(
    val model: String,
    val prompt: String,
    val stream: Boolean = false,
    val system: String = ""
)

@Serializable
@JsonIgnoreUnknownKeys
data class OllamaGenerateResponse(
    val model: String? = null,
    val response: String? = null,
    val done: Boolean? = null
)

@Serializable
data class OllamaChatRequest(
    val model: String,
    val messages: List<Message>,
    val stream: Boolean = false
)

@Serializable
@JsonIgnoreUnknownKeys
data class OllamaChatResponse(
    val model: String? = null,
    @SerialName("message")
    val message: Message? = null,
    val done: Boolean? = null
)

@Serializable
data class Message(
    val role: String,
    val content: String
)

@Serializable
data class OllamaResponse(
    val model: String? = null,
    @SerialName("message")
    val message: Message? = null,
    val done: Boolean? = null
)
