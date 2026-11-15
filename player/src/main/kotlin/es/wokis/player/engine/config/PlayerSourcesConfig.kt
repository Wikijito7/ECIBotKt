package es.wokis.player.engine.config

/**
 * Gateway-neutral source-provider configuration for the shared Lavaplayer engine.
 *
 * The root Kord application maps its existing `Config` sections onto this type, so the same
 * source registration (YouTube, LavaSrc providers, Kokoro TTS, local and remote sources) is used
 * by both the Kord adapter and the isolated JDA runtime. Defaults leave every optional provider
 * disabled; local files and the generic remote fallback stay available because they need no
 * credentials.
 */
data class PlayerSourcesConfig(
    val youtube: YoutubeSourceConfig = YoutubeSourceConfig(),
    val deezer: DeezerSourceConfig = DeezerSourceConfig(),
    val spotify: SpotifySourceConfig = SpotifySourceConfig(),
    val tidal: TidalSourceConfig = TidalSourceConfig(),
    val kokoro: KokoroSourceConfig = KokoroSourceConfig(),
)

data class YoutubeSourceConfig(
    val enabled: Boolean = false,
    val oauth2Token: String? = null,
    val poToken: String? = null,
    val visitorData: String? = null,
    val remoteCipherUrl: String? = null,
    val remoteCipherPassword: String? = null,
)

data class DeezerSourceConfig(
    val enabled: Boolean = false,
    val masterDecryptionKey: String = "",
    val arlToken: String = "",
)

data class SpotifySourceConfig(
    val enabled: Boolean = false,
    val clientId: String = "",
    val clientSecret: String = "",
    val customEndpoint: String = "",
)

data class TidalSourceConfig(
    val enabled: Boolean = false,
    val countryCode: String = "",
    val token: String = "",
)

data class KokoroSourceConfig(
    val baseUrl: String = "",
    val defaultVoice: String = "",
    val defaultSpeed: Float = 1.0f,
    val defaultLangCode: String = "",
)
