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
    val huggingChat: HuggingChatConfig,
    @SerialName("deezer")
    val deezer: DeezerConfig,
    @SerialName("spotify")
    val spotify: SpotifyConfig,
    @SerialName("tidal")
    val tidal: TidalConfig
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
    @SerialName("enabled")
    val enabled: Boolean,
    @SerialName("oauth2_token")
    val oauth2Token: String?,
    @SerialName("po_token")
    val poToken: String?,
    @SerialName("visitor_data")
    val visitorData: String?,
    @SerialName("remote_cipher_url")
    val remoteCipherUrl: String?,
    @SerialName("remote_cipher_password")
    val remoteCipherPassword: String?
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

@Serializable
data class DeezerConfig(
    @SerialName("enabled")
    val enabled: Boolean,
    @SerialName("master_decryption_key")
    val masterDecryptionKey: String,
    @SerialName("arl_token")
    val arlToken: String,
)

@Serializable
data class SpotifyConfig(
    @SerialName("enabled")
    val enabled: Boolean,
    @SerialName("client_id")
    val clientId: String,
    @SerialName("client_secret")
    val clientSecret: String,
    @SerialName("custom_endpoint")
    val customEndpoint: String
)

@Serializable
data class TidalConfig(
    @SerialName("enabled")
    val enabled: Boolean,
    @SerialName("country_code")
    val countryCode: String,
    @SerialName("token")
    val token: String
)
