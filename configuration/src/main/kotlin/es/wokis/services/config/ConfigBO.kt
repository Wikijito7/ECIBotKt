package es.wokis.services.config

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Serializable shared configuration.
 *
 * The JSON property names are part of the public contract: the same `./data/config.json` schema is
 * read by the root Kord runtime and the isolated JDA runtime, so every [SerialName] must stay
 * byte-compatible with the deployed files. Adding an optional field requires a default value;
 * required fields stay required so a partial file fails fast.
 *
 * [toString] is deliberately redacted. `Config` holds the Discord token and every provider secret,
 * so logging it must never print the values; only booleans and "is set" presence flags are rendered.
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
    val kokoro: KokoroConfig
) {
    override fun toString(): String =
        "Config(debug=$debug, databaseEnabled=${database.enabled}, youtubeEnabled=${youtube.enabled}, " +
            "huggingChatEnabled=${huggingChat.enabled}, deezerEnabled=${deezer.enabled}, " +
            "spotifyEnabled=${spotify.enabled}, tidalEnabled=${tidal.enabled}, kokoroBaseUrlSet=${kokoro.baseUrl.isNotEmpty()})"
}

/**
 * Redacted like every other section: only the structural [enabled] boolean and derived
 * "is set" presence flags are rendered. Credentials **and** the database name are withheld, so
 * logging a configuration can never print a deployment detail.
 */
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

/**
 * Redacted like every other section. The remote-cipher endpoint and its password are reported
 * separately — [remoteCipherUrl] as `remoteCipherConfigured` and [remoteCipherPassword] as
 * `remoteCipherPasswordSet` — because an endpoint without a password (or the reverse) is a
 * misconfiguration worth seeing in a log, while neither value may ever be rendered.
 */
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

/**
 * Redacted like every other section: only the structural [enabled] boolean and derived
 * "is set" flags are rendered, so logging a configuration can never print the country code
 * or the token value.
 */
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

/**
 * Redacted like every other section: [baseUrl], [defaultVoice], [defaultSpeed] and
 * [defaultLangCode] are only reported as "is set" flags. The speed is a number, so it is never
 * rendered either: a configured non-zero speed yields `defaultSpeedSet=true`, and the value
 * itself stays out of the log line.
 */
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
