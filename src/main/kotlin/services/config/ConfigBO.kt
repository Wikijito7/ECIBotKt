package es.wokis.services.config

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Config(
    val key: String,
    @SerialName("openai_key")
    val openaiKey: String,
    @SerialName("yt_oauth2_token")
    val youtubeOauth2Token: String,
    val debug: Boolean,
    val database: Database,
    @SerialName("hugging_chat")
    val huggingChat: HuggingChat
)

@Serializable
data class HuggingChat(
    val user: String,
    val password: String
)

@Serializable
data class Database(
    val enabled: Boolean,
    val username: String,
    val password: String,
    val database: String
)
