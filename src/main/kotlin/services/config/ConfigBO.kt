package es.wokis.services.config

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Config(
    @SerialName("discord_bot_token")
    val discordBotToken: String,
    @SerialName("debug")
    val debug: Boolean,
    @SerialName("database")
    val database: DatabaseConfig,
    @SerialName("youtube")
    val youtube: YouTubeConfig,
    @SerialName("hugging_chat")
    val huggingChat: HuggingChatConfig
)

@Serializable
data class DatabaseConfig(
    @SerialName("enabled")
    val enabled: Boolean,
    @SerialName("username")
    val username: String,
    @SerialName("password")
    val password: String,
    @SerialName("database")
    val database: String
)

@Serializable
data class YouTubeConfig(
    @SerialName("oauth2_token")
    val oauth2Token: String?
)

@Serializable
data class HuggingChatConfig(
    @SerialName("enabled")
    val enabled: Boolean,
    @SerialName("user")
    val user: String,
    @SerialName("password")
    val password: String
)
