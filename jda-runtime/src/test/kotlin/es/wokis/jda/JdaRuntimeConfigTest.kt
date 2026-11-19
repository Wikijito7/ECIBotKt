package es.wokis.jda

import es.wokis.exceptions.EmptyDeezerMasterDecryptionKeyException
import es.wokis.exceptions.EmptyDiscordTokenException
import es.wokis.player.engine.config.DeezerSourceConfig
import es.wokis.player.engine.config.KokoroSourceConfig
import es.wokis.player.engine.config.PlayerSourcesConfig
import es.wokis.player.engine.config.SpotifySourceConfig
import es.wokis.player.engine.config.TidalSourceConfig
import es.wokis.player.engine.config.YoutubeSourceConfig
import es.wokis.player.engine.config.toPlayerSourcesConfig
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
import kotlinx.serialization.descriptors.elementNames
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
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
    fun `Given a shared config When fromConfig Then map its token and provider sources`() {
        val sharedConfig = sampleConfig(
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

        val runtimeConfig = JdaRuntimeConfig.fromConfig(sharedConfig)

        assertEquals(SECRET_TOKEN, runtimeConfig.token)
        assertEquals(expectedProviderSources(), runtimeConfig.sources)
        assertEquals(sharedConfig.toPlayerSourcesConfig(), runtimeConfig.sources)
    }

    @Test
    fun `Given a shared config token When fromConfig Then keep it unchanged and out of the rendered value`() {
        val sharedConfig = sampleConfig(token = FILE_TOKEN_SENTINEL)

        val runtimeConfig = JdaRuntimeConfig.fromConfig(sharedConfig)

        assertEquals(FILE_TOKEN_SENTINEL, runtimeConfig.token)
        assertEquals("JdaRuntimeConfig(tokenSet=true)", runtimeConfig.toString())
        assertFalse(runtimeConfig.toString().contains(FILE_TOKEN_SENTINEL))
    }

    @Test
    fun `Given a blank token in the shared config When fromConfig Then fail safely without echoing it`() {
        val sharedConfig = sampleConfig(token = "   ")

        val error = assertThrows(JdaRuntimeConfigException::class.java) {
            JdaRuntimeConfig.fromConfig(sharedConfig)
        }

        val message = error.message.orEmpty()
        assertTrue(message.contains("discord_bot_token"), "the failure must name the token property: $message")
        assertFalse(message.contains(SECRET_TOKEN), "the failure leaked a token: $message")
    }

    @Test
    fun `Given a built config When rendered Then never include the token or provider secrets`() {
        val sharedConfig = sampleConfig(
            token = SECRET_TOKEN,
            youtube = YouTubeConfig(
                enabled = true,
                oauth2Token = YOUTUBE_OAUTH2_SENTINEL,
                poToken = "",
                visitorData = null,
                remoteCipherUrl = "",
                remoteCipherPassword = null,
            ),
        )
        val config = JdaRuntimeConfig.fromConfig(sharedConfig)

        val rendered = config.toString()

        assertEquals("JdaRuntimeConfig(tokenSet=true)", rendered)
        assertFalse(rendered.contains(SECRET_TOKEN))
        assertFalse(rendered.contains(YOUTUBE_OAUTH2_SENTINEL))
    }

    @Test
    fun `Given a shared config without debug When fromConfig runs Then default the radio endpoint flag to false`() {
        val runtimeConfig = JdaRuntimeConfig.fromConfig(sampleConfig())

        assertFalse(runtimeConfig.debug)
        assertFalse(JdaRuntimeConfig(token = SECRET_TOKEN).debug)
    }

    @Test
    fun `Given a shared config with debug When fromConfig runs Then forward the radio endpoint flag`() {
        val runtimeConfig = JdaRuntimeConfig.fromConfig(sampleConfig(debug = true))

        assertTrue(runtimeConfig.debug)
        assertEquals("JdaRuntimeConfig(tokenSet=true)", runtimeConfig.toString())
    }

    @Test
    fun `Given the runtime config type When inspected Then it declares no guild configuration at all`() {
        // Commands are registered in every guild the client is in, so no guild id is configured.
        val guildMembers = (
            JdaRuntimeConfig::class.java.declaredFields.map { field -> field.name } +
                JdaRuntimeConfig::class.java.declaredMethods.map { method -> method.name } +
                JdaRuntimeConfig::class.java.declaredClasses.flatMap { declared ->
                    declared.declaredFields.map { field -> field.name } +
                        declared.declaredMethods.map { method -> method.name }
                }
            ).filter { name -> name.contains("guild", ignoreCase = true) }

        assertTrue(
            guildMembers.isEmpty(),
            "JdaRuntimeConfig must not configure a guild destination: $guildMembers",
        )
    }

    @Test
    fun `Given the shared Config schema When inspected Then it declares no JDA development guild field`() {
        val elementNames = Config.serializer().descriptor.elementNames
        assertFalse(
            elementNames.contains("discord_dev_guild_id"),
            "the shared schema must not declare a JDA guild id: $elementNames",
        )
        assertTrue(elementNames.contains("discord_bot_token"))
    }

    @Test
    fun `Given a custom path When loadSharedConfig runs Then load exactly that path`() {
        val loader = mockk<ConfigLoader>()
        val capturedPath = slot<Path>()
        val customPath = tempDir.resolve("jda-shared-config.json")
        every { loader.load(capture(capturedPath)) } returns sampleConfig()

        loadSharedConfig(path = customPath, loader = loader)

        assertEquals(customPath, capturedPath.captured)
    }

    @Test
    fun `Given no path When loadSharedConfig runs Then use the shared default path`() {
        val loader = mockk<ConfigLoader>()
        val capturedPath = slot<Path>()
        every { loader.load(capture(capturedPath)) } returns sampleConfig()

        loadSharedConfig(loader = loader)

        assertEquals(DEFAULT_CONFIG_PATH, capturedPath.captured)
        assertEquals(Path.of("./data", "config.json"), DEFAULT_CONFIG_PATH)
    }

    @Test
    fun `Given a synthetic shared JSON file When loadSharedConfig runs Then the token only comes from the JSON`() {
        val target = tempDir.resolve("data").resolve("config.json")
        Files.createDirectories(target.parent)
        val sharedConfig = sampleConfig(token = JSON_TOKEN_SENTINEL)
        Files.writeString(target, Json.encodeToString(Config.serializer(), sharedConfig))

        val runtimeConfig = JdaRuntimeConfig.fromConfig(loadSharedConfig(path = target, loader = ConfigLoader()))

        assertEquals(JSON_TOKEN_SENTINEL, runtimeConfig.token)
        assertFalse(runtimeConfig.toString().contains(JSON_TOKEN_SENTINEL))
    }

    @Test
    fun `Given a loader failure carrying raw content When loadSharedConfig runs Then sanitize it without a cause`() {
        // A decoder-style failure whose message could echo the raw configuration.
        val loader = mockk<ConfigLoader>()
        every { loader.load(any<Path>()) } throws IllegalArgumentException("raw $SECRET_TOKEN")

        val error = assertThrows(JdaRuntimeConfigException::class.java) {
            loadSharedConfig(loader = loader)
        }

        val message = error.message.orEmpty()
        assertTrue(message.contains(DEFAULT_CONFIG_PATH.toString()), "the failure must name the file: $message")
        assertTrue(message.contains("invalid"), "the failure must name the decoding category: $message")
        assertFalse(message.contains(SECRET_TOKEN), "the failure leaked the raw configuration: $message")
        assertNull(error.cause, "a decoder-originated failure must not be chained")
    }

    @Test
    fun `Given an empty token failure When loadSharedConfig runs Then name the file and chain the safe validation cause`() {
        val loader = mockk<ConfigLoader>()
        every { loader.load(any<Path>()) } throws EmptyDiscordTokenException()

        val error = assertThrows(JdaRuntimeConfigException::class.java) {
            loadSharedConfig(loader = loader)
        }

        val message = error.message.orEmpty()
        assertTrue(message.contains(DEFAULT_CONFIG_PATH.toString()), "the failure must name the file: $message")
        assertTrue(message.contains("empty or blank"), "the failure must name the token rule: $message")
        assertTrue(error.cause is EmptyDiscordTokenException)
    }

    @Test
    fun `Given a Deezer key failure When loadSharedConfig runs Then name the file without provider secrets`() {
        val loader = mockk<ConfigLoader>()
        every { loader.load(any<Path>()) } throws EmptyDeezerMasterDecryptionKeyException()

        val error = assertThrows(JdaRuntimeConfigException::class.java) {
            loadSharedConfig(loader = loader)
        }

        val message = error.message.orEmpty()
        assertTrue(message.contains(DEFAULT_CONFIG_PATH.toString()), "the failure must name the file: $message")
        assertTrue(message.contains("Deezer"), "the failure must name the provider rule: $message")
        assertTrue(error.cause is EmptyDeezerMasterDecryptionKeyException)
        assertFalse(message.contains(SECRET_TOKEN), "the failure leaked a configured secret: $message")
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
        debug: Boolean = false,
        database: DatabaseConfig = DatabaseConfig(false, "db_user", "db_password", "db_name"),
        youtube: YouTubeConfig = YouTubeConfig(false, null, null, null, null, null),
        huggingChat: HuggingChatConfig = HuggingChatConfig(false, "hugging_user", "hugging_password"),
        deezer: DeezerConfig = DeezerConfig(false, "", ""),
        spotify: SpotifyConfig = SpotifyConfig(false, "", "", ""),
        tidal: TidalConfig = TidalConfig(false, "ES", ""),
        kokoro: KokoroConfig = KokoroConfig("", "", 1.0f, ""),
    ): Config = Config(
        discordBotToken = token,
        debug = debug,
        database = database,
        youtube = youtube,
        huggingChat = huggingChat,
        deezer = deezer,
        spotify = spotify,
        tidal = tidal,
        kokoro = kokoro,
    )

    private companion object {
        private const val SECRET_TOKEN = "synthetic-jda-token-sentinel"
        private const val FILE_TOKEN_SENTINEL = "file-token-sentinel"
        private const val JSON_TOKEN_SENTINEL = "json-only-token-sentinel"
        private const val YOUTUBE_OAUTH2_SENTINEL = "youtube-oauth2-sentinel"
        private const val DEEZER_KEY_SENTINEL = "deezer-key-sentinel"
        private const val SPOTIFY_CLIENT_ID_SENTINEL = "spotify-client-id-sentinel"
        private const val SPOTIFY_CLIENT_SECRET_SENTINEL = "spotify-client-secret-sentinel"
        private const val TIDAL_TOKEN_SENTINEL = "tidal-token-sentinel"
        private const val KOKORO_URL_SENTINEL = "kokoro-url-sentinel"
    }
}
