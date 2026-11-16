package es.wokis.services.config

import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConfigRedactionTest {

    @Test
    fun `Given sentinel credentials When Config is rendered Then no token provider or database secret is exposed`() {
        // Given
        val config = Config(
            discordBotToken = TOKEN_SENTINEL,
            debug = true,
            database = DatabaseConfig(
                enabled = true,
                username = DATABASE_USER_SENTINEL,
                password = DATABASE_PASSWORD_SENTINEL,
                database = DATABASE_NAME_SENTINEL,
            ),
            youtube = YouTubeConfig(
                enabled = true,
                oauth2Token = YOUTUBE_OAUTH2_SENTINEL,
                poToken = YOUTUBE_PO_TOKEN_SENTINEL,
                visitorData = YOUTUBE_VISITOR_SENTINEL,
                remoteCipherUrl = YOUTUBE_REMOTE_URL_SENTINEL,
                remoteCipherPassword = YOUTUBE_REMOTE_PASSWORD_SENTINEL,
            ),
            huggingChat = HuggingChatConfig(
                enabled = true,
                user = HUGGING_USER_SENTINEL,
                password = HUGGING_PASSWORD_SENTINEL,
            ),
            deezer = DeezerConfig(
                enabled = true,
                masterDecryptionKey = DEEZER_KEY_SENTINEL,
                arlToken = DEEZER_ARL_SENTINEL,
            ),
            spotify = SpotifyConfig(
                enabled = true,
                clientId = SPOTIFY_CLIENT_ID_SENTINEL,
                clientSecret = SPOTIFY_CLIENT_SECRET_SENTINEL,
                customEndpoint = SPOTIFY_ENDPOINT_SENTINEL,
            ),
            tidal = TidalConfig(
                enabled = true,
                countryCode = "ES",
                token = TIDAL_TOKEN_SENTINEL,
            ),
            kokoro = KokoroConfig(
                baseUrl = KOKORO_URL_SENTINEL,
                defaultVoice = KOKORO_VOICE_SENTINEL,
                defaultSpeed = KOKORO_SPEED_SENTINEL,
                defaultLangCode = KOKORO_LANG_SENTINEL,
            ),
        )

        // When
        val rendered = config.toString()

        // Then
        SENSITIVE_VALUES.forEach { sentinel ->
            assertFalse(rendered.contains(sentinel), "Config.toString leaked a configured secret: $sentinel")
        }
        assertTrue(rendered.contains("debug=true"))
        assertTrue(rendered.contains("databaseEnabled=true"))
        assertTrue(rendered.contains("youtubeEnabled=true"))
        assertTrue(rendered.contains("huggingChatEnabled=true"))
        assertTrue(rendered.contains("deezerEnabled=true"))
        assertTrue(rendered.contains("spotifyEnabled=true"))
        assertTrue(rendered.contains("tidalEnabled=true"))
        assertTrue(rendered.contains("kokoroBaseUrlSet=true"))
    }

    @Test
    fun `Given a Kokoro section When rendered standalone Then hide its endpoint voice language and speed values`() {
        // Given
        val kokoro = KokoroConfig(
            baseUrl = KOKORO_URL_SENTINEL,
            defaultVoice = KOKORO_VOICE_SENTINEL,
            defaultSpeed = KOKORO_SPEED_SENTINEL,
            defaultLangCode = KOKORO_LANG_SENTINEL,
        )
        // `default_speed` is required, so `0` is the only encoding of "not configured".
        val kokoroWithoutSpeed = KokoroConfig(
            baseUrl = KOKORO_URL_SENTINEL,
            defaultVoice = KOKORO_VOICE_SENTINEL,
            defaultSpeed = 0f,
            defaultLangCode = KOKORO_LANG_SENTINEL,
        )

        // When
        val rendered = kokoro.toString()
        val renderedWithoutSpeed = kokoroWithoutSpeed.toString()

        // Then: a standalone section must be as redacted as the same section nested in Config.
        listOf(
            KOKORO_URL_SENTINEL,
            KOKORO_VOICE_SENTINEL,
            KOKORO_LANG_SENTINEL,
            KOKORO_SPEED_SENTINEL.toString(),
        ).forEach { sentinel ->
            assertFalse(rendered.contains(sentinel), "KokoroConfig.toString leaked a configured value: $sentinel")
        }
        assertTrue(rendered.contains("KokoroConfig("))
        assertTrue(
            rendered.contains("defaultSpeedSet=true"),
            "KokoroConfig.toString must report defaultSpeedSet=true: $rendered",
        )
        // ...and an unconfigured speed must not be reported as set.
        assertTrue(
            renderedWithoutSpeed.contains("defaultSpeedSet=false"),
            "KokoroConfig.toString must report defaultSpeedSet=false: $renderedWithoutSpeed",
        )
    }

    @Test
    fun `Given a Tidal section When rendered standalone Then hide its country code and token`() {
        // Given: uniform redaction policy means a standalone section never renders configured values.
        val tidal = TidalConfig(
            enabled = true,
            countryCode = TIDAL_COUNTRY_SENTINEL,
            token = TIDAL_TOKEN_SENTINEL,
        )

        // When
        val rendered = tidal.toString()

        // Then: neither the token nor the country code may appear in the rendered section.
        assertFalse(
            rendered.contains(TIDAL_TOKEN_SENTINEL),
            "TidalConfig.toString leaked the configured token: $rendered",
        )
        assertFalse(
            rendered.contains(TIDAL_COUNTRY_SENTINEL),
            "TidalConfig.toString leaked the configured country code: $rendered",
        )
        assertTrue(rendered.contains("TidalConfig("))
    }

    @Test
    fun `Given a rendered database section When inspected Then its credentials and database name stay out of the text`() {
        // Given
        val database = DatabaseConfig(
            enabled = true,
            username = DATABASE_USER_SENTINEL,
            password = DATABASE_PASSWORD_SENTINEL,
            database = DATABASE_NAME_SENTINEL,
        )

        // When
        val rendered = database.toString()

        // Then: the standalone section withholds every configured value...
        listOf(
            DATABASE_USER_SENTINEL,
            DATABASE_PASSWORD_SENTINEL,
            DATABASE_NAME_SENTINEL,
        ).forEach { sentinel ->
            assertFalse(rendered.contains(sentinel), "DatabaseConfig.toString leaked a configured value: $rendered")
        }

        // ...while still exposing the structural status and presence flags.
        assertTrue(rendered.contains("DatabaseConfig("))
        assertTrue(rendered.contains("enabled=true"))
        assertTrue(rendered.contains("usernameSet=true"))
        assertTrue(rendered.contains("passwordSet=true"))
        assertTrue(rendered.contains("databaseSet=true"))
    }

    @Test
    fun `Given sentinel credentials When provider sections render Then presence flags are informative`() {
        // Given: each section carries a configured secret that a standalone toString must never print.
        val huggingChat = HuggingChatConfig(
            enabled = true,
            user = HUGGING_USER_SENTINEL,
            password = HUGGING_PASSWORD_SENTINEL,
        )
        val deezer = DeezerConfig(
            enabled = true,
            masterDecryptionKey = DEEZER_KEY_SENTINEL,
            arlToken = DEEZER_ARL_SENTINEL,
        )
        val spotify = SpotifyConfig(
            enabled = true,
            clientId = SPOTIFY_CLIENT_ID_SENTINEL,
            clientSecret = SPOTIFY_CLIENT_SECRET_SENTINEL,
            customEndpoint = SPOTIFY_ENDPOINT_SENTINEL,
        )

        // When
        val huggingChatRendered = huggingChat.toString()
        val deezerRendered = deezer.toString()
        val spotifyRendered = spotify.toString()

        // Then: each section reports that the new credential is set...
        assertTrue(
            huggingChatRendered.contains("passwordSet=true"),
            "HuggingChatConfig.toString must report passwordSet=true: $huggingChatRendered",
        )
        assertTrue(
            deezerRendered.contains("arlTokenSet=true"),
            "DeezerConfig.toString must report arlTokenSet=true: $deezerRendered",
        )
        assertTrue(
            spotifyRendered.contains("clientSecretSet=true"),
            "SpotifyConfig.toString must report clientSecretSet=true: $spotifyRendered",
        )

        // ...without rendering any configured value from the same section.
        listOf(HUGGING_USER_SENTINEL, HUGGING_PASSWORD_SENTINEL).forEach { sentinel ->
            assertFalse(
                huggingChatRendered.contains(sentinel),
                "HuggingChatConfig.toString leaked a configured value: $sentinel",
            )
        }
        listOf(DEEZER_KEY_SENTINEL, DEEZER_ARL_SENTINEL).forEach { sentinel ->
            assertFalse(
                deezerRendered.contains(sentinel),
                "DeezerConfig.toString leaked a configured value: $sentinel",
            )
        }
        listOf(
            SPOTIFY_CLIENT_ID_SENTINEL,
            SPOTIFY_CLIENT_SECRET_SENTINEL,
            SPOTIFY_ENDPOINT_SENTINEL,
        ).forEach { sentinel ->
            assertFalse(
                spotifyRendered.contains(sentinel),
                "SpotifyConfig.toString leaked a configured value: $sentinel",
            )
        }
    }

    @Test
    fun `Given a YouTube section When rendered standalone Then report the cipher password flag without leaking values`() {
        // Given: the remote-cipher endpoint and its password are separate secrets that must not be printed.
        val youtube = YouTubeConfig(
            enabled = true,
            oauth2Token = YOUTUBE_OAUTH2_SENTINEL,
            poToken = YOUTUBE_PO_TOKEN_SENTINEL,
            visitorData = YOUTUBE_VISITOR_SENTINEL,
            remoteCipherUrl = YOUTUBE_REMOTE_URL_SENTINEL,
            remoteCipherPassword = YOUTUBE_REMOTE_PASSWORD_SENTINEL,
        )
        // An endpoint without its password is a misconfiguration, so the two flags stay independent.
        val youtubeWithoutPassword = YouTubeConfig(
            enabled = true,
            oauth2Token = null,
            poToken = null,
            visitorData = null,
            remoteCipherUrl = YOUTUBE_REMOTE_URL_SENTINEL,
            remoteCipherPassword = null,
        )

        // When
        val rendered = youtube.toString()
        val renderedWithoutPassword = youtubeWithoutPassword.toString()

        // Then: the configured password is reported as set...
        assertTrue(
            rendered.contains("remoteCipherPasswordSet=true"),
            "YouTubeConfig.toString must report remoteCipherPasswordSet=true: $rendered",
        )
        // ...while a missing password is reported as absent, independently of the configured endpoint.
        assertTrue(
            renderedWithoutPassword.contains("remoteCipherPasswordSet=false"),
            "YouTubeConfig.toString must report remoteCipherPasswordSet=false: $renderedWithoutPassword",
        )
        assertTrue(
            renderedWithoutPassword.contains("remoteCipherConfigured=true"),
            "YouTubeConfig.toString must keep the endpoint flag independent: $renderedWithoutPassword",
        )

        // Neither rendering may print a configured value.
        listOf(
            YOUTUBE_OAUTH2_SENTINEL,
            YOUTUBE_PO_TOKEN_SENTINEL,
            YOUTUBE_VISITOR_SENTINEL,
            YOUTUBE_REMOTE_URL_SENTINEL,
            YOUTUBE_REMOTE_PASSWORD_SENTINEL,
        ).forEach { sentinel ->
            assertFalse(rendered.contains(sentinel), "YouTubeConfig.toString leaked a configured value: $sentinel")
            assertFalse(
                renderedWithoutPassword.contains(sentinel),
                "YouTubeConfig.toString leaked a configured value: $sentinel",
            )
        }
    }

    private companion object {
        private const val TOKEN_SENTINEL = "redaction-token-sentinel"
        private const val DATABASE_USER_SENTINEL = "redaction-db-user-sentinel"
        private const val DATABASE_PASSWORD_SENTINEL = "redaction-db-password-sentinel"
        private const val DATABASE_NAME_SENTINEL = "redaction-db-name-sentinel"
        private const val YOUTUBE_OAUTH2_SENTINEL = "redaction-youtube-oauth2-sentinel"
        private const val YOUTUBE_PO_TOKEN_SENTINEL = "redaction-youtube-po-token-sentinel"
        private const val YOUTUBE_VISITOR_SENTINEL = "redaction-youtube-visitor-sentinel"
        private const val YOUTUBE_REMOTE_URL_SENTINEL = "redaction-youtube-remote-url-sentinel"
        private const val YOUTUBE_REMOTE_PASSWORD_SENTINEL = "redaction-youtube-remote-password-sentinel"
        private const val HUGGING_USER_SENTINEL = "redaction-hugging-user-sentinel"
        private const val HUGGING_PASSWORD_SENTINEL = "redaction-hugging-password-sentinel"
        private const val DEEZER_KEY_SENTINEL = "redaction-deezer-key-sentinel"
        private const val DEEZER_ARL_SENTINEL = "redaction-deezer-arl-sentinel"
        private const val SPOTIFY_CLIENT_ID_SENTINEL = "redaction-spotify-client-id-sentinel"
        private const val SPOTIFY_CLIENT_SECRET_SENTINEL = "redaction-spotify-client-secret-sentinel"
        private const val SPOTIFY_ENDPOINT_SENTINEL = "redaction-spotify-endpoint-sentinel"
        private const val TIDAL_TOKEN_SENTINEL = "redaction-tidal-token-sentinel"
        private const val TIDAL_COUNTRY_SENTINEL = "redaction-tidal-country-sentinel"
        private const val KOKORO_URL_SENTINEL = "redaction-kokoro-url-sentinel"
        private const val KOKORO_VOICE_SENTINEL = "redaction-kokoro-voice-sentinel"
        private const val KOKORO_LANG_SENTINEL = "redaction-kokoro-lang-sentinel"
        private const val KOKORO_SPEED_SENTINEL = 7.37f

        private val SENSITIVE_VALUES = listOf(
            TOKEN_SENTINEL,
            DATABASE_USER_SENTINEL,
            DATABASE_PASSWORD_SENTINEL,
            DATABASE_NAME_SENTINEL,
            YOUTUBE_OAUTH2_SENTINEL,
            YOUTUBE_PO_TOKEN_SENTINEL,
            YOUTUBE_VISITOR_SENTINEL,
            YOUTUBE_REMOTE_URL_SENTINEL,
            YOUTUBE_REMOTE_PASSWORD_SENTINEL,
            HUGGING_USER_SENTINEL,
            HUGGING_PASSWORD_SENTINEL,
            DEEZER_KEY_SENTINEL,
            DEEZER_ARL_SENTINEL,
            SPOTIFY_CLIENT_ID_SENTINEL,
            SPOTIFY_CLIENT_SECRET_SENTINEL,
            SPOTIFY_ENDPOINT_SENTINEL,
            TIDAL_TOKEN_SENTINEL,
            KOKORO_URL_SENTINEL,
            KOKORO_VOICE_SENTINEL,
            KOKORO_LANG_SENTINEL,
            KOKORO_SPEED_SENTINEL.toString(),
        )
    }
}
