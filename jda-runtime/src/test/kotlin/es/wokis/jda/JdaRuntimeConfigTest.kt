package es.wokis.jda

import es.wokis.domain.model.GuildId
import es.wokis.player.engine.config.DeezerSourceConfig
import es.wokis.player.engine.config.KokoroSourceConfig
import es.wokis.player.engine.config.PlayerSourcesConfig
import es.wokis.player.engine.config.SpotifySourceConfig
import es.wokis.player.engine.config.TidalSourceConfig
import es.wokis.player.engine.config.YoutubeSourceConfig
import es.wokis.services.config.Config
import es.wokis.services.config.ConfigLoader
import es.wokis.services.config.DEFAULT_CONFIG_PATH
import es.wokis.services.config.DatabaseConfig
import es.wokis.services.config.DeezerConfig
import es.wokis.services.config.HuggingChatConfig
import es.wokis.services.config.KokoroConfig
import es.wokis.services.config.SpotifyConfig
import es.wokis.services.config.TidalConfig
import es.wokis.services.config.YouTubeConfig
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class JdaRuntimeConfigTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `Given a valid shared configuration file When fromEnv Then read the token guild and provider sources from it`() {
        // Given
        val config = sampleConfig(
            token = SECRET_TOKEN,
            youtube = YouTubeConfig(
                enabled = true,
                oauth2Token = YOUTUBE_OAUTH2_SENTINEL,
                poToken = "",
                visitorData = null,
                remoteCipherUrl = "",
                remoteCipherPassword = null,
            ),
            deezer = DeezerConfig(enabled = true, masterDecryptionKey = DEEZER_KEY_SENTINEL, arlToken = ""),
            spotify = SpotifyConfig(
                enabled = true,
                clientId = SPOTIFY_CLIENT_ID_SENTINEL,
                clientSecret = SPOTIFY_CLIENT_SECRET_SENTINEL,
                customEndpoint = "",
            ),
            tidal = TidalConfig(enabled = true, countryCode = "ES", token = TIDAL_TOKEN_SENTINEL),
            kokoro = KokoroConfig(
                baseUrl = KOKORO_URL_SENTINEL,
                defaultVoice = "",
                defaultSpeed = 1.0f,
                defaultLangCode = "es",
            ),
        )
        val path = writeConfig(config)

        // When
        val runtimeConfig = JdaRuntimeConfig.fromEnv(env(configPath = path.toString(), guildId = "42"))

        // Then
        assertEquals(SECRET_TOKEN, runtimeConfig.token)
        assertEquals(GuildId(42uL), runtimeConfig.guildId)
        assertEquals(expectedProviderSources(), runtimeConfig.sources)
    }

    @Test
    fun `Given a token only in the environment When fromEnv Then the shared configuration file is the token source`() {
        // Given
        val path = writeConfig(sampleConfig(token = FILE_TOKEN_SENTINEL))

        // When
        val runtimeConfig = JdaRuntimeConfig.fromEnv(
            env(configPath = path.toString(), guildId = "42") +
                mapOf("DISCORD_BOT_TOKEN" to ENV_TOKEN_SENTINEL),
        )

        // Then
        assertEquals(FILE_TOKEN_SENTINEL, runtimeConfig.token)
        assertFalse(runtimeConfig.toString().contains(ENV_TOKEN_SENTINEL))
    }

    @Test
    fun `Given a blank config path When fromEnv Then fall back to the documented default path`() {
        // Given
        val loader = mockk<ConfigLoader>()
        val capturedPath = slot<Path>()
        every { loader.load(capture(capturedPath)) } returns sampleConfig()

        // When
        JdaRuntimeConfig.fromEnv(env(configPath = "   ", guildId = "42"), loader)

        // Then
        assertEquals(DEFAULT_CONFIG_PATH, capturedPath.captured)
    }

    @Test
    fun `Given an absent config path When fromEnv Then fall back to the documented default path`() {
        // Given
        val loader = mockk<ConfigLoader>()
        val capturedPath = slot<Path>()
        every { loader.load(capture(capturedPath)) } returns sampleConfig()

        // When
        JdaRuntimeConfig.fromEnv(env(configPath = null, guildId = "42"), loader)

        // Then
        assertEquals(DEFAULT_CONFIG_PATH, capturedPath.captured)
        assertEquals(Path.of("./data", "config.json"), DEFAULT_CONFIG_PATH)
    }

    @Test
    fun `Given a surrounding whitespace config path When fromEnv Then trim the path`() {
        // Given
        val loader = mockk<ConfigLoader>()
        val capturedPath = slot<Path>()
        every { loader.load(capture(capturedPath)) } returns sampleConfig()

        // When
        JdaRuntimeConfig.fromEnv(env(configPath = "  /tmp/jda-staging.json  ", guildId = "42"), loader)

        // Then
        assertEquals(Path.of("/tmp/jda-staging.json"), capturedPath.captured)
    }

    @Test
    fun `Given a surrounding whitespace guild id When fromEnv Then trim it`() {
        // Given
        val path = writeConfig(sampleConfig())

        // When
        val runtimeConfig = JdaRuntimeConfig.fromEnv(env(configPath = path.toString(), guildId = "  42  "))

        // Then
        assertEquals(GuildId(42uL), runtimeConfig.guildId)
    }

    @Test
    fun `Given missing guild id When fromEnv Then fail naming the variable without the token`() {
        // When
        val error = expectConfigError(
            env(configPath = writeConfig(sampleConfig()).toString(), guildId = null),
        )

        // Then
        assertTrue(error.message.orEmpty().contains(JdaRuntimeConfig.ENV_GUILD_ID))
        assertFalse(error.message.orEmpty().contains(SECRET_TOKEN))
    }

    @Test
    fun `Given blank guild id When fromEnv Then fail naming the variable without the token`() {
        // When
        val error = expectConfigError(
            env(configPath = writeConfig(sampleConfig()).toString(), guildId = "   "),
        )

        // Then
        assertTrue(error.message.orEmpty().contains(JdaRuntimeConfig.ENV_GUILD_ID))
        assertFalse(error.message.orEmpty().contains(SECRET_TOKEN))
    }

    @Test
    fun `Given a non numeric guild id When fromEnv Then fail without the token`() {
        // When
        val error = expectConfigError(
            env(configPath = writeConfig(sampleConfig()).toString(), guildId = "not-a-guild"),
        )

        // Then
        assertTrue(error.message.orEmpty().contains(JdaRuntimeConfig.ENV_GUILD_ID))
        assertFalse(error.message.orEmpty().contains(SECRET_TOKEN))
    }

    @Test
    fun `Given a zero guild id When fromEnv Then reject global registration`() {
        // When
        val error = expectConfigError(
            env(configPath = writeConfig(sampleConfig()).toString(), guildId = "0"),
        )

        // Then
        assertTrue(error.message.orEmpty().contains(JdaRuntimeConfig.ENV_GUILD_ID))
        assertTrue(error.message.orEmpty().contains("positive"))
        assertFalse(error.message.orEmpty().contains(SECRET_TOKEN))
    }

    @Test
    fun `Given a negative guild id When fromEnv Then reject it`() {
        // When
        val error = expectConfigError(
            env(configPath = writeConfig(sampleConfig()).toString(), guildId = "-1"),
        )

        // Then
        assertTrue(error.message.orEmpty().contains(JdaRuntimeConfig.ENV_GUILD_ID))
        assertTrue(error.message.orEmpty().contains("positive"))
        assertFalse(error.message.orEmpty().contains(SECRET_TOKEN))
    }

    @Test
    fun `Given a guild id larger than Long max When fromEnv Then reject the overflow`() {
        // When
        val error = expectConfigError(
            env(configPath = writeConfig(sampleConfig()).toString(), guildId = "9223372036854775808"),
        )

        // Then
        assertTrue(error.message.orEmpty().contains(JdaRuntimeConfig.ENV_GUILD_ID))
        assertFalse(error.message.orEmpty().contains(SECRET_TOKEN))
    }

    @Test
    fun `Given an unsigned overflow guild id When fromEnv Then reject the overflow`() {
        // When
        val error = expectConfigError(
            env(configPath = writeConfig(sampleConfig()).toString(), guildId = "18446744073709551616"),
        )

        // Then
        assertTrue(error.message.orEmpty().contains(JdaRuntimeConfig.ENV_GUILD_ID))
        assertFalse(error.message.orEmpty().contains(SECRET_TOKEN))
    }

    @Test
    fun `Given an empty token in the shared file When fromEnv Then fail naming the file without any secret`() {
        // Given
        val path = writeConfig(sampleConfig(token = ""))

        // When
        val error = expectConfigError(env(configPath = path.toString(), guildId = "42"))

        // Then
        assertTrue(error.message.orEmpty().contains(path.toString()))
        assertTrue(error.message.orEmpty().contains("empty or blank"))
        assertTrue(error.message.orEmpty().contains("discord_bot_token"))
        assertFalse(error.message.orEmpty().contains(SECRET_TOKEN))
    }

    @Test
    fun `Given Deezer enabled without its key When fromEnv Then name the file without echoing provider secrets`() {
        // Given
        val path = writeConfig(
            sampleConfig(
                token = SECRET_TOKEN,
                deezer = DeezerConfig(enabled = true, masterDecryptionKey = "", arlToken = DEEZER_ARL_SENTINEL),
                spotify = SpotifyConfig(
                    enabled = false,
                    clientId = "",
                    clientSecret = SPOTIFY_CLIENT_SECRET_SENTINEL,
                    customEndpoint = "",
                ),
            ),
        )

        // When
        val error = expectConfigError(env(configPath = path.toString(), guildId = "42"))

        // Then
        assertTrue(error.message.orEmpty().contains(path.toString()))
        assertTrue(error.message.orEmpty().contains("Deezer"))
        assertFalse(error.message.orEmpty().contains(SECRET_TOKEN))
        assertFalse(error.message.orEmpty().contains(DEEZER_ARL_SENTINEL))
        assertFalse(error.message.orEmpty().contains(SPOTIFY_CLIENT_SECRET_SENTINEL))
    }

    @Test
    fun `Given a malformed shared file When fromEnv Then report its type without echoing the raw content`() {
        // Given
        val path = tempDir.resolve("config.json")
        Files.writeString(path, "{ \"discord_bot_token\": \"$SECRET_TOKEN\", \"debug\": ")

        // When
        val error = expectConfigError(env(configPath = path.toString(), guildId = "42"))

        // Then
        assertTrue(error.message.orEmpty().contains(path.toString()))
        assertTrue(error.message.orEmpty().contains("invalid"))
        assertFalse(error.message.orEmpty().contains(SECRET_TOKEN))

        // Then: every cause in the chain is inspected, not only the top-level runtime message.
        val causeChain = generateSequence<Throwable>(error) { it.cause }.toList()
        assertTrue(causeChain.isNotEmpty(), "the thrown failure must be the first cause-chain element")
        val renderedChain = causeChain.joinToString(separator = "\n") { cause ->
            "${cause.javaClass.name}: ${cause.message}"
        }
        assertFalse(
            renderedChain.contains(SECRET_TOKEN),
            "the failure cause chain leaked the raw configuration secret: $renderedChain",
        )
        causeChain.forEach { cause ->
            assertFalse(
                cause.message.orEmpty().contains(SECRET_TOKEN),
                "a nested cause leaked the raw configuration secret: ${cause.javaClass.name}",
            )
        }
    }

    @Test
    fun `Given a whitespace-only token in the shared file When fromEnv Then reject it as empty or blank`() {
        // Given
        val path = writeConfig(sampleConfig(token = "   "))

        // When
        val error = expectConfigError(env(configPath = path.toString(), guildId = "42"))

        // Then: the runtime reports the file and the empty-or-blank rule, never the configured token value.
        assertTrue(error.message.orEmpty().contains(path.toString()))
        assertTrue(error.message.orEmpty().contains("empty or blank"))
        assertTrue(error.message.orEmpty().contains("discord_bot_token"))
        assertFalse(error.message.orEmpty().contains(SECRET_TOKEN))
    }

    @Test
    fun `Given a padded nonblank token in the shared file When fromEnv Then keep it unchanged`() {
        // Given
        val paddedToken = "  $SECRET_TOKEN  "
        val path = writeConfig(sampleConfig(token = paddedToken))

        // When
        val runtimeConfig = JdaRuntimeConfig.fromEnv(env(configPath = path.toString(), guildId = "42"))

        // Then: rejecting blanks must not trim or rewrite a valid token.
        assertEquals(paddedToken, runtimeConfig.token)
    }

    @Test
    fun `Given a built config When rendered Then never include the token or provider secrets`() {
        // Given
        val path = writeConfig(
            sampleConfig(
                token = SECRET_TOKEN,
                youtube = YouTubeConfig(
                    enabled = true,
                    oauth2Token = YOUTUBE_OAUTH2_SENTINEL,
                    poToken = "",
                    visitorData = null,
                    remoteCipherUrl = "",
                    remoteCipherPassword = null,
                ),
            ),
        )
        val config = JdaRuntimeConfig.fromEnv(env(configPath = path.toString(), guildId = "42"))

        // When
        val rendered = config.toString()

        // Then
        assertEquals("JdaRuntimeConfig(guildId=42)", rendered)
        assertFalse(rendered.contains(SECRET_TOKEN))
        assertFalse(rendered.contains(YOUTUBE_OAUTH2_SENTINEL))
    }

    private fun writeConfig(config: Config): Path {
        val path = tempDir.resolve("config.json")
        Files.createDirectories(path.parent)
        Files.writeString(path, Json.encodeToString(config))
        return path
    }

    private fun expectedProviderSources(): PlayerSourcesConfig = PlayerSourcesConfig(
        youtube = YoutubeSourceConfig(
            enabled = true,
            oauth2Token = YOUTUBE_OAUTH2_SENTINEL,
            poToken = "",
            visitorData = null,
            remoteCipherUrl = "",
            remoteCipherPassword = null,
        ),
        deezer = DeezerSourceConfig(
            enabled = true,
            masterDecryptionKey = DEEZER_KEY_SENTINEL,
            arlToken = "",
        ),
        spotify = SpotifySourceConfig(
            enabled = true,
            clientId = SPOTIFY_CLIENT_ID_SENTINEL,
            clientSecret = SPOTIFY_CLIENT_SECRET_SENTINEL,
            customEndpoint = "",
        ),
        tidal = TidalSourceConfig(enabled = true, countryCode = "ES", token = TIDAL_TOKEN_SENTINEL),
        kokoro = KokoroSourceConfig(
            baseUrl = KOKORO_URL_SENTINEL,
            defaultVoice = "",
            defaultSpeed = 1.0f,
            defaultLangCode = "es",
        ),
    )

    private fun sampleConfig(
        token: String = SECRET_TOKEN,
        database: DatabaseConfig = DatabaseConfig(false, "db_user", "db_password", "db_name"),
        youtube: YouTubeConfig = YouTubeConfig(false, null, null, null, null, null),
        huggingChat: HuggingChatConfig = HuggingChatConfig(false, "hugging_user", "hugging_password"),
        deezer: DeezerConfig = DeezerConfig(false, "", ""),
        spotify: SpotifyConfig = SpotifyConfig(false, "", "", ""),
        tidal: TidalConfig = TidalConfig(false, "ES", ""),
        kokoro: KokoroConfig = KokoroConfig("", "", 1.0f, ""),
    ): Config = Config(
        discordBotToken = token,
        debug = false,
        database = database,
        youtube = youtube,
        huggingChat = huggingChat,
        deezer = deezer,
        spotify = spotify,
        tidal = tidal,
        kokoro = kokoro,
    )

    private fun env(configPath: String?, guildId: String?): Map<String, String> = buildMap {
        configPath?.let { put(JdaRuntimeConfig.ENV_CONFIG_PATH, it) }
        guildId?.let { put(JdaRuntimeConfig.ENV_GUILD_ID, it) }
    }

    private fun expectConfigError(env: Map<String, String>): JdaRuntimeConfigException =
        assertThrows(JdaRuntimeConfigException::class.java) { JdaRuntimeConfig.fromEnv(env) }

    private companion object {
        private const val SECRET_TOKEN = "synthetic-jda-token-sentinel"
        private const val FILE_TOKEN_SENTINEL = "file-token-sentinel"
        private const val ENV_TOKEN_SENTINEL = "environment-token-must-not-be-used"
        private const val YOUTUBE_OAUTH2_SENTINEL = "youtube-oauth2-sentinel"
        private const val DEEZER_KEY_SENTINEL = "deezer-key-sentinel"
        private const val DEEZER_ARL_SENTINEL = "deezer-arl-sentinel"
        private const val SPOTIFY_CLIENT_ID_SENTINEL = "spotify-client-id-sentinel"
        private const val SPOTIFY_CLIENT_SECRET_SENTINEL = "spotify-client-secret-sentinel"
        private const val TIDAL_TOKEN_SENTINEL = "tidal-token-sentinel"
        private const val KOKORO_URL_SENTINEL = "kokoro-url-sentinel"
    }
}
