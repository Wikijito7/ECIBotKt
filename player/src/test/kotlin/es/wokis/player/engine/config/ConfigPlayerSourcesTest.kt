package es.wokis.player.engine.config

import es.wokis.services.config.Config
import es.wokis.services.config.DatabaseConfig
import es.wokis.services.config.DeezerConfig
import es.wokis.services.config.HuggingChatConfig
import es.wokis.services.config.KokoroConfig
import es.wokis.services.config.SpotifyConfig
import es.wokis.services.config.TidalConfig
import es.wokis.services.config.YouTubeConfig
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ConfigPlayerSourcesTest {

    @Test
    fun `Given every provider configured When mapped Then carry every field to the neutral source configuration`() {
        // Given
        val config = Config(
            discordBotToken = "mapping-token",
            debug = false,
            database = DatabaseConfig(false, "user", "password", "database"),
            youtube = YouTubeConfig(
                enabled = true,
                oauth2Token = "oauth2",
                poToken = "po-token",
                visitorData = "visitor-data",
                remoteCipherUrl = "https://cipher.example/player.js",
                remoteCipherPassword = "cipher-password",
            ),
            huggingChat = HuggingChatConfig(false, "user", "password"),
            deezer = DeezerConfig(
                enabled = true,
                masterDecryptionKey = "deezer-key",
                arlToken = "deezer-arl",
            ),
            spotify = SpotifyConfig(
                enabled = true,
                clientId = "spotify-id",
                clientSecret = "spotify-secret",
                customEndpoint = "https://spotify.example",
            ),
            tidal = TidalConfig(
                enabled = true,
                countryCode = "ES",
                token = "tidal-token",
            ),
            kokoro = KokoroConfig(
                baseUrl = "http://kokoro:8880",
                defaultVoice = "af_bella",
                defaultSpeed = 1.25f,
                defaultLangCode = "en-US",
            ),
        )

        // When
        val sources = config.toPlayerSourcesConfig()

        // Then
        assertEquals(everyProviderSources(), sources)
    }

    @Test
    fun `Given nullable and empty provider credentials When mapped Then null stays null and empty stays empty`() {
        // Given
        val config = Config(
            discordBotToken = "mapping-token",
            debug = false,
            database = DatabaseConfig(false, "user", "password", "database"),
            youtube = YouTubeConfig(
                enabled = false,
                oauth2Token = null,
                poToken = "",
                visitorData = null,
                remoteCipherUrl = "",
                remoteCipherPassword = null,
            ),
            huggingChat = HuggingChatConfig(false, "", ""),
            deezer = DeezerConfig(enabled = false, masterDecryptionKey = "", arlToken = ""),
            spotify = SpotifyConfig(enabled = false, clientId = "", clientSecret = "", customEndpoint = ""),
            tidal = TidalConfig(enabled = false, countryCode = "ES", token = ""),
            kokoro = KokoroConfig(baseUrl = "", defaultVoice = "", defaultSpeed = 1.0f, defaultLangCode = ""),
        )

        // When
        val sources = config.toPlayerSourcesConfig()

        // Then
        assertNull(sources.youtube.oauth2Token)
        assertEquals("", sources.youtube.poToken)
        assertNull(sources.youtube.visitorData)
        assertEquals("", sources.youtube.remoteCipherUrl)
        assertNull(sources.youtube.remoteCipherPassword)
        assertEquals("", sources.deezer.masterDecryptionKey)
        assertEquals("", sources.deezer.arlToken)
        assertEquals("", sources.spotify.clientId)
        assertEquals("", sources.spotify.clientSecret)
        assertEquals("", sources.spotify.customEndpoint)
        assertEquals("", sources.tidal.token)
        assertEquals("", sources.kokoro.baseUrl)
        assertEquals("", sources.kokoro.defaultVoice)
        assertEquals("", sources.kokoro.defaultLangCode)
    }

    private fun everyProviderSources(): PlayerSourcesConfig = PlayerSourcesConfig(
        youtube = YoutubeSourceConfig(
            enabled = true,
            oauth2Token = "oauth2",
            poToken = "po-token",
            visitorData = "visitor-data",
            remoteCipherUrl = "https://cipher.example/player.js",
            remoteCipherPassword = "cipher-password",
        ),
        deezer = DeezerSourceConfig(
            enabled = true,
            masterDecryptionKey = "deezer-key",
            arlToken = "deezer-arl",
        ),
        spotify = SpotifySourceConfig(
            enabled = true,
            clientId = "spotify-id",
            clientSecret = "spotify-secret",
            customEndpoint = "https://spotify.example",
        ),
        tidal = TidalSourceConfig(
            enabled = true,
            countryCode = "ES",
            token = "tidal-token",
        ),
        kokoro = KokoroSourceConfig(
            baseUrl = "http://kokoro:8880",
            defaultVoice = "af_bella",
            defaultSpeed = 1.25f,
            defaultLangCode = "en-US",
        ),
    )
}
