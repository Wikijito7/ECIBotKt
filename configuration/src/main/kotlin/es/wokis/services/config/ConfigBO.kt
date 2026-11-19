package es.wokis.services.config

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Shared `./data/config.json` schema; the [SerialName]s are part of the deployed contract.
 *
 * [toString] is redacted so the Discord token and provider secrets never reach logs.
 */
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
    val tidal: TidalConfig,
    @SerialName("kokoro")
    val kokoro: KokoroConfig,
    /** Staging log target for the JDA runtime; blank disables the file tee. */
    @SerialName("log_file")
    val logFile: String = "./data/logs/jda-runtime.log",
) {
    override fun toString(): String =
        "Config(debug=$debug, databaseEnabled=${database.enabled}, youtubeEnabled=${youtube.enabled}, " +
            "huggingChatEnabled=${huggingChat.enabled}, deezerEnabled=${deezer.enabled}, " +
            "spotifyEnabled=${spotify.enabled}, tidalEnabled=${tidal.enabled}, " +
            "kokoroBaseUrlSet=${kokoro.baseUrl.isNotEmpty()}, logFileSet=${logFile.isNotBlank()})"
}

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
) {
    override fun toString(): String =
        "DatabaseConfig(enabled=$enabled, usernameSet=${username.isNotEmpty()}, " +
            "passwordSet=${password.isNotEmpty()}, databaseSet=${database.isNotEmpty()})"
}

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
) {
    override fun toString(): String =
        "YouTubeConfig(enabled=$enabled, oauth2TokenSet=${oauth2Token != null}, poTokenSet=${poToken != null}, " +
            "visitorDataSet=${visitorData != null}, remoteCipherConfigured=${remoteCipherUrl != null}, " +
            "remoteCipherPasswordSet=${remoteCipherPassword != null})"
}

@Serializable
data class HuggingChatConfig(
    @SerialName("enabled")
    val enabled: Boolean,
    @SerialName("user")
    val user: String,
    @SerialName("password")
    val password: String
) {
    override fun toString(): String =
        "HuggingChatConfig(enabled=$enabled, userSet=${user.isNotEmpty()}, " +
            "passwordSet=${password.isNotEmpty()})"
}

@Serializable
data class DeezerConfig(
    @SerialName("enabled")
    val enabled: Boolean,
    @SerialName("master_decryption_key")
    val masterDecryptionKey: String,
    @SerialName("arl_token")
    val arlToken: String,
) {
    override fun toString(): String =
        "DeezerConfig(enabled=$enabled, credentialsSet=${masterDecryptionKey.isNotEmpty()}, " +
            "arlTokenSet=${arlToken.isNotEmpty()})"
}

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
) {
    override fun toString(): String =
        "SpotifyConfig(enabled=$enabled, clientIdSet=${clientId.isNotEmpty()}, " +
            "clientSecretSet=${clientSecret.isNotEmpty()}, " +
            "customEndpointSet=${customEndpoint.isNotEmpty()})"
}

@Serializable
data class TidalConfig(
    @SerialName("enabled")
    val enabled: Boolean,
    @SerialName("country_code")
    val countryCode: String,
    @SerialName("token")
    val token: String
) {
    override fun toString(): String =
        "TidalConfig(enabled=$enabled, countryCodeSet=${countryCode.isNotEmpty()}, tokenSet=${token.isNotEmpty()})"
}

@Serializable
data class KokoroConfig(
    @SerialName("base_url")
    val baseUrl: String,
    @SerialName("default_voice")
    val defaultVoice: String,
    @SerialName("default_speed")
    val defaultSpeed: Float,
    @SerialName("default_lang_code")
    val defaultLangCode: String
) {
    override fun toString(): String =
        "KokoroConfig(baseUrlSet=${baseUrl.isNotEmpty()}, defaultVoiceSet=${defaultVoice.isNotEmpty()}, " +
            "defaultSpeedSet=${defaultSpeed != UNSET_SPEED}, " +
            "defaultLangCodeSet=${defaultLangCode.isNotEmpty()})"

    private companion object {
        /** `default_speed` is required, so `0` is the only value that can mean "not configured". */
        private const val UNSET_SPEED = 0f
    }
}
