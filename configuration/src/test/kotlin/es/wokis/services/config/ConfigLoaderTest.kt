package es.wokis.services.config

import es.wokis.exceptions.ConfigDecodingException
import es.wokis.exceptions.EmptyDeezerMasterDecryptionKeyException
import es.wokis.exceptions.EmptyDiscordTokenException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.elementNames
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConfigLoaderTest {

    @TempDir
    lateinit var tempDir: Path

    private val templateText: String
        get() = requireNotNull(javaClass.getResource(CONFIG_TEMPLATE_RESOURCE)).readText()

    @Test
    fun `Given a missing file When load runs Then seed it from the production template and validate it`() {
        val target = tempDir.resolve("nested/data/config.json")

        val error = assertFailsWith<EmptyDiscordTokenException> { ConfigLoader().load(target) }

        // The template is copied byte for byte, including its empty token that fails validation.
        assertTrue(Files.exists(target))
        assertEquals(templateText, Files.readString(target))
        assertEquals("Discord token shouldn't be empty", error.message)
    }

    @Test
    fun `Given the shared loader defaults When inspected Then use the documented repository-root config path`() {
        assertEquals(Path.of("./data", "config.json"), DEFAULT_CONFIG_PATH)
    }

    @Test
    fun `Given a Kord config without any JDA guild field When load runs Then decode the existing schema unchanged`() {
        val target = writeConfig(configJson())

        val loaded = ConfigLoader().load(target)

        assertEquals(SECRET_TOKEN, loaded.discordBotToken)
        assertFalse(loaded.debug)
        assertNull(loaded.youtube.oauth2Token)
    }

    @Test
    fun `Given the shared Config schema When inspected Then it declares no JDA development guild field`() {
        // Guild-scoped JDA registration comes from the client's guild list, never from JSON.
        val elementNames = Config.serializer().descriptor.elementNames
        assertFalse(
            elementNames.contains("discord_dev_guild_id"),
            "the shared schema must not declare a JDA guild id: $elementNames",
        )
        assertTrue(elementNames.contains("discord_bot_token"), "the historical Kord schema must stay intact")
    }

    @Test
    fun `Given a Kord config carrying discord_dev_guild_id When load runs Then strict decoding rejects the key`() {
        val target = writeConfig(configJson(jdaDevGuildId = "987654321"))

        val error = assertFailsWith<ConfigDecodingException> { ConfigLoader().load(target) }

        val message = error.message.orEmpty()
        assertTrue(message.contains(target.toString()), "the failure must name the file: $message")
        assertTrue(message.contains("JSON decoding"), "the failure must name the phase: $message")
        assertFalse(message.contains("987654321"), "the failure echoed the rejected value: $message")
        assertNull(error.cause, "a decoder-originated failure must not be chained")
    }

    @Test
    fun `Given an existing configuration When load runs Then keep the deployed file untouched`() {
        val content = configJson(token = SECRET_TOKEN).replace("\"debug\": false", "\"debug\": true")
        val target = writeConfig(content)

        val loaded = ConfigLoader().load(target)

        assertEquals(SECRET_TOKEN, loaded.discordBotToken)
        assertTrue(loaded.debug)
        assertEquals(content, Files.readString(target), "an existing configuration must never be rewritten")
    }

    @Test
    fun `Given an invalid existing file When load runs Then fail without replacing it with the template`() {
        val content = "{ \"discord_bot_token\": \"$SECRET_TOKEN\", \"debug\": "
        val target = writeConfig(content)

        assertFailsWith<SerializationException> { ConfigLoader().load(target) }

        assertEquals(content, Files.readString(target), "the loader must only seed a missing file")
    }

    @Test
    fun `Given a malformed file holding a secret When load runs Then fail naming the file without echoing the payload`() {
        // A malformed document whose raw text a decoder error could echo back.
        val fixtureText = malformedFixtureText()
        assertTrue(fixtureText.contains(MALFORMED_JSON_SENTINEL), "the fixture must carry the secret sentinel")
        val target = writeConfig(fixtureText)

        val error = assertFails { ConfigLoader().load(target) }

        assertTrue(error is SerializationException, "malformed JSON must still fail at the decoding phase")
        val message = error.message.orEmpty()
        assertTrue(message.isNotBlank(), "the sanitized failure must keep a readable message")
        assertTrue(
            message.contains(target.fileName.toString()) || message.contains("config", ignoreCase = true),
            "the failure must identify the configuration file, but was: $message",
        )

        // Neither the thrown exception nor any cause in its chain may echo the raw JSON payload.
        val readableChain = generateSequence<Throwable>(error) { it.cause }
            .joinToString(separator = "\n") { "${it.javaClass.name}: ${it.message}" }
        assertFalse(
            readableChain.contains(MALFORMED_JSON_SENTINEL),
            "the failure chain leaked the raw configuration payload: $readableChain",
        )

        assertEquals(fixtureText, Files.readString(target), "the loader must only seed a missing file")
    }

    @Test
    fun `Given an unknown root key When load runs Then reject it as strict JSON`() {
        val target = writeConfig(
            configJson().replace("\"debug\": false,", "\"debug\": false, \"unexpected\": \"$SECRET_TOKEN\","),
        )

        val error = assertFailsWith<SerializationException> { ConfigLoader().load(target) }

        assertTrue(error.message.orEmpty().contains("JSON decoding"))
        assertFalse(error.message.orEmpty().contains(SECRET_TOKEN))
    }

    @Test
    fun `Given the loader construction surface When inspected Then strict decoding cannot be replaced by an injected codec`() {
        // Every constructor signature is inspected, including Kotlin's default-argument bridge.
        val injectedCodecParameters = ConfigLoader::class.java.constructors
            .flatMap { constructor -> constructor.parameterTypes.asList() }
            .filter { type -> Json::class.java.isAssignableFrom(type) }

        // Strict decoding is a structural guarantee, not a caller-supplied Json instance that
        // could enable ignoreUnknownKeys or lenient parsing.
        assertTrue(
            injectedCodecParameters.isEmpty(),
            "ConfigLoader must not accept a Json codec, but found: $injectedCodecParameters",
        )
    }

    @Test
    fun `Given a missing required section When load runs Then fail instead of applying a default section`() {
        val target = writeConfig(configJson(includeDatabase = false))

        val error = assertFailsWith<SerializationException> { ConfigLoader().load(target) }

        assertTrue(error.message.orEmpty().contains("JSON decoding"))
        assertFalse(error.message.orEmpty().contains(SECRET_TOKEN))
    }

    @Test
    fun `Given a whitespace-only Discord token When load runs Then reject it as empty`() {
        val target = writeConfig(configJson(token = "   "))

        val error = assertFailsWith<EmptyDiscordTokenException> { ConfigLoader().load(target) }

        assertEquals("Discord token shouldn't be empty", error.message)
    }

    @Test
    fun `Given a whitespace-only token When Config validate runs directly Then reject the shared rule`() {
        val config = Json.decodeFromString<Config>(configJson(token = "   "))

        val error = assertFailsWith<EmptyDiscordTokenException> { config.validate() }

        assertEquals("Discord token shouldn't be empty", error.message)
    }

    @Test
    fun `Given a padded nonblank Discord token When load runs Then keep it unchanged`() {
        val paddedToken = "  $SECRET_TOKEN  "
        val target = writeConfig(configJson(token = paddedToken))

        val loaded = ConfigLoader().load(target)

        assertEquals(paddedToken, loaded.discordBotToken)
    }

    @Test
    fun `Given an empty Discord token When load runs Then keep the existing validation rule`() {
        val target = writeConfig(configJson(token = ""))

        val error = assertFailsWith<EmptyDiscordTokenException> { ConfigLoader().load(target) }

        assertEquals("Discord token shouldn't be empty", error.message)
    }

    @Test
    fun `Given Deezer enabled without a master key When load runs Then keep the existing validation rule`() {
        val target = writeConfig(
            configJson(
                token = SECRET_TOKEN,
                deezerEnabled = true,
                deezerKey = "",
            ),
        )

        val error = assertFailsWith<EmptyDeezerMasterDecryptionKeyException> { ConfigLoader().load(target) }

        assertEquals("Deezer is enabled but no master decryption key was provided", error.message)
        assertFalse(error.message.orEmpty().contains(SECRET_TOKEN))
        assertFalse(error.message.orEmpty().contains(HUGGING_PASSWORD))
    }

    @Test
    fun `Given nullable YouTube credentials When load runs Then preserve null and empty values exactly`() {
        val target = writeConfig(
            configJson(
                youtube = YouTubeJson(
                    oauth2Token = null,
                    poToken = "",
                    visitorData = null,
                    remoteCipherUrl = "",
                    remoteCipherPassword = null,
                ),
            ),
        )

        val loaded = ConfigLoader().load(target)

        assertNull(loaded.youtube.oauth2Token)
        assertEquals("", loaded.youtube.poToken)
        assertNull(loaded.youtube.visitorData)
        assertEquals("", loaded.youtube.remoteCipherUrl)
        assertNull(loaded.youtube.remoteCipherPassword)
    }

    @Test
    fun `Given two missing files When loaded by the same loader Then every load opens a fresh template stream`() {
        val loader = ConfigLoader()
        val first = tempDir.resolve("first/config.json")
        val second = tempDir.resolve("second/config.json")

        assertFailsWith<EmptyDiscordTokenException> { loader.load(first) }
        assertFailsWith<EmptyDiscordTokenException> { loader.load(second) }

        // A cached, already consumed stream would leave the second copy empty.
        assertEquals(templateText, Files.readString(first))
        assertEquals(templateText, Files.readString(second))
    }

    @Test
    fun `Given an unavailable template When a file is missing Then fail naming the resource without config values`() {
        val target = tempDir.resolve("config.json")
        val loader = ConfigLoader(template = { null })

        val error = assertFailsWith<IllegalStateException> { loader.load(target) }

        assertTrue(error.message.orEmpty().contains(CONFIG_TEMPLATE_RESOURCE))
        assertFalse(Files.exists(target))
    }

    private fun malformedFixtureText(): String =
        requireNotNull(javaClass.getResource(MALFORMED_JSON_FIXTURE)).readText()

    private fun writeConfig(content: String): Path {
        val target = tempDir.resolve("config.json")
        Files.createDirectories(target.parent)
        Files.writeString(target, content)
        return target
    }

    private fun configJson(
        token: String = SECRET_TOKEN,
        includeDatabase: Boolean = true,
        jdaDevGuildId: String? = null,
        deezerEnabled: Boolean = false,
        deezerKey: String = "",
        youtube: YouTubeJson = YouTubeJson(),
    ): String = buildString {
        appendLine("{")
        appendLine("  \"discord_bot_token\": \"$token\",")
        appendLine("  \"debug\": false,")
        if (jdaDevGuildId != null) {
            appendLine("  \"discord_dev_guild_id\": \"$jdaDevGuildId\",")
        }
        if (includeDatabase) {
            appendLine(
                "  \"database\": {\"enabled\": false, \"username\": \"db_user\", " +
                    "\"password\": \"db_password\", \"database\": \"db_name\"},",
            )
        }
        appendLine("  \"youtube\": {")
        appendLine("    \"enabled\": false,")
        appendLine("    \"oauth2_token\": ${quoted(youtube.oauth2Token)},")
        appendLine("    \"po_token\": ${quoted(youtube.poToken)},")
        appendLine("    \"visitor_data\": ${quoted(youtube.visitorData)},")
        appendLine("    \"remote_cipher_url\": ${quoted(youtube.remoteCipherUrl)},")
        appendLine("    \"remote_cipher_password\": ${quoted(youtube.remoteCipherPassword)}")
        appendLine("  },")
        appendLine(
            "  \"hugging_chat\": {\"enabled\": false, \"user\": \"hugging_user\", " +
                "\"password\": \"$HUGGING_PASSWORD\"},",
        )
        appendLine(
            "  \"deezer\": {\"enabled\": $deezerEnabled, " +
                "\"master_decryption_key\": \"$deezerKey\", \"arl_token\": \"\"},",
        )
        appendLine(
            "  \"spotify\": {\"enabled\": false, \"client_id\": \"client_id\", " +
                "\"client_secret\": \"client_secret\", \"custom_endpoint\": \"\"},",
        )
        appendLine("  \"tidal\": {\"enabled\": false, \"country_code\": \"ES\", \"token\": \"\"},")
        appendLine(
            "  \"kokoro\": {\"base_url\": \"\", \"default_voice\": \"\", " +
                "\"default_speed\": 1.0, \"default_lang_code\": \"\"}",
        )
        appendLine("}")
    }

    private data class YouTubeJson(
        val oauth2Token: String? = null,
        val poToken: String? = null,
        val visitorData: String? = null,
        val remoteCipherUrl: String? = null,
        val remoteCipherPassword: String? = null,
    )

    private fun quoted(value: String?): String = value?.let { "\"$it\"" } ?: "null"

    private companion object {
        private const val SECRET_TOKEN = "loader-secret-token-sentinel"
        private const val HUGGING_PASSWORD = "loader-hugging-password-sentinel"
        private const val MALFORMED_JSON_SENTINEL = "malformed-json-secret-sentinel"
        private const val MALFORMED_JSON_FIXTURE = "/fixtures/malformed_config_with_secret.json"
    }
}
