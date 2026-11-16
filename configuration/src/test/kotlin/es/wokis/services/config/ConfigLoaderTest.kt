package es.wokis.services.config

import es.wokis.exceptions.EmptyDeezerMasterDecryptionKeyException
import es.wokis.exceptions.EmptyDiscordTokenException
import kotlinx.serialization.SerializationException
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
        // Given
        val target = tempDir.resolve("nested/data/config.json")

        // When
        val error = assertFailsWith<EmptyDiscordTokenException> { ConfigLoader().load(target) }

        // Then: the template is copied byte for byte, including its empty token that fails validation.
        assertTrue(Files.exists(target))
        assertEquals(templateText, Files.readString(target))
        assertEquals("Discord token shouldn't be empty", error.message)
    }

    @Test
    fun `Given an existing configuration When load runs Then keep the deployed file untouched`() {
        // Given
        val content = configJson(token = SECRET_TOKEN).replace("\"debug\": false", "\"debug\": true")
        val target = writeConfig(content)

        // When
        val loaded = ConfigLoader().load(target)

        // Then
        assertEquals(SECRET_TOKEN, loaded.discordBotToken)
        assertTrue(loaded.debug)
        assertEquals(content, Files.readString(target), "an existing configuration must never be rewritten")
    }

    @Test
    fun `Given an invalid existing file When load runs Then fail without replacing it with the template`() {
        // Given
        val content = "{ \"discord_bot_token\": \"$SECRET_TOKEN\", \"debug\": "
        val target = writeConfig(content)

        // When
        assertFailsWith<SerializationException> { ConfigLoader().load(target) }

        // Then
        assertEquals(content, Files.readString(target), "the loader must only seed a missing file")
    }

    @Test
    fun `Given a malformed file holding a secret When load runs Then fail naming the file without echoing the payload`() {
        // Given: the fixture is a malformed document whose raw text a decoder error could echo back.
        val fixtureText = malformedFixtureText()
        assertTrue(fixtureText.contains(MALFORMED_JSON_SENTINEL), "the fixture must carry the secret sentinel")
        val target = writeConfig(fixtureText)

        // When
        val error = assertFails { ConfigLoader().load(target) }

        // Then: strict decoding still rejects the document and the failure stays readable.
        assertTrue(error is SerializationException, "malformed JSON must still fail at the decoding phase")
        val message = error.message.orEmpty()
        assertTrue(message.isNotBlank(), "the sanitized failure must keep a readable message")
        assertTrue(
            message.contains(target.fileName.toString()) || message.contains("config", ignoreCase = true),
            "the failure must identify the configuration file, but was: $message",
        )

        // Then: neither the thrown exception nor any cause in its chain may echo the raw JSON payload.
        val readableChain = generateSequence<Throwable>(error) { it.cause }
            .joinToString(separator = "\n") { "${it.javaClass.name}: ${it.message}" }
        assertFalse(
            readableChain.contains(MALFORMED_JSON_SENTINEL),
            "the failure chain leaked the raw configuration payload: $readableChain",
        )

        // Then: the malformed deployment file is never replaced or rewritten by the loader.
        assertEquals(fixtureText, Files.readString(target), "the loader must only seed a missing file")
    }

    @Test
    fun `Given an unknown root key When load runs Then reject it as strict JSON`() {
        // Given
        val target = writeConfig(
            configJson().replace("\"debug\": false,", "\"debug\": false, \"unexpected\": \"$SECRET_TOKEN\","),
        )

        // When
        val error = assertFailsWith<SerializationException> { ConfigLoader().load(target) }

        // Then: strict decoding rejects the unknown key; the sanitized message names the decode phase
        // instead of echoing the offending payload.
        assertTrue(error.message.orEmpty().contains("JSON decoding"))
        assertFalse(error.message.orEmpty().contains(SECRET_TOKEN))
    }

    @Test
    fun `Given the loader construction surface When inspected Then strict decoding cannot be replaced by an injected codec`() {
        // When: every constructor signature is inspected, including Kotlin's default-argument bridge.
        val injectedCodecParameters = ConfigLoader::class.java.constructors
            .flatMap { constructor -> constructor.parameterTypes.asList() }
            .filter { type -> Json::class.java.isAssignableFrom(type) }

        // Then: strict decoding is a structural guarantee, not a caller-supplied Json instance that
        // could enable ignoreUnknownKeys or lenient parsing.
        assertTrue(
            injectedCodecParameters.isEmpty(),
            "ConfigLoader must not accept a Json codec, but found: $injectedCodecParameters",
        )
    }

    @Test
    fun `Given a missing required section When load runs Then fail instead of applying a default section`() {
        // Given
        val target = writeConfig(configJson(includeDatabase = false))

        // When
        val error = assertFailsWith<SerializationException> { ConfigLoader().load(target) }

        // Then: a partial schema still fails instead of defaulting; the message stays sanitized.
        assertTrue(error.message.orEmpty().contains("JSON decoding"))
        assertFalse(error.message.orEmpty().contains(SECRET_TOKEN))
    }

    @Test
    fun `Given a whitespace-only Discord token When load runs Then reject it as empty`() {
        // Given
        val target = writeConfig(configJson(token = "   "))

        // When
        val error = assertFailsWith<EmptyDiscordTokenException> { ConfigLoader().load(target) }

        // Then: the shared validation rule treats a whitespace-only token exactly like an empty one.
        assertEquals("Discord token shouldn't be empty", error.message)
    }

    @Test
    fun `Given a whitespace-only token When Config validate runs directly Then reject the shared rule`() {
        // Given: decoded without the loader so Config.validate is the only rule under test.
        val config = Json.decodeFromString<Config>(configJson(token = "   "))

        // When
        val error = assertFailsWith<EmptyDiscordTokenException> { config.validate() }

        // Then
        assertEquals("Discord token shouldn't be empty", error.message)
    }

    @Test
    fun `Given a padded nonblank Discord token When load runs Then keep it unchanged`() {
        // Given: a valid credential with surrounding spaces that must survive validation untouched.
        val paddedToken = "  $SECRET_TOKEN  "
        val target = writeConfig(configJson(token = paddedToken))

        // When
        val loaded = ConfigLoader().load(target)

        // Then: rejecting blanks must not trim or rewrite a nonblank token.
        assertEquals(paddedToken, loaded.discordBotToken)
    }

    @Test
    fun `Given an empty Discord token When load runs Then keep the existing validation rule`() {
        // Given
        val target = writeConfig(configJson(token = ""))

        // When
        val error = assertFailsWith<EmptyDiscordTokenException> { ConfigLoader().load(target) }

        // Then
        assertEquals("Discord token shouldn't be empty", error.message)
    }

    @Test
    fun `Given Deezer enabled without a master key When load runs Then keep the existing validation rule`() {
        // Given
        val target = writeConfig(
            configJson(
                token = SECRET_TOKEN,
                deezerEnabled = true,
                deezerKey = "",
            ),
        )

        // When
        val error = assertFailsWith<EmptyDeezerMasterDecryptionKeyException> { ConfigLoader().load(target) }

        // Then: the validation message never echoes the raw configuration values.
        assertEquals("Deezer is enabled but no master decryption key was provided", error.message)
        assertFalse(error.message.orEmpty().contains(SECRET_TOKEN))
        assertFalse(error.message.orEmpty().contains(HUGGING_PASSWORD))
    }

    @Test
    fun `Given nullable YouTube credentials When load runs Then preserve null and empty values exactly`() {
        // Given
        val target = writeConfig(
            configJson(
                oauth2Token = null,
                poToken = "",
                visitorData = null,
                remoteCipherUrl = "",
                remoteCipherPassword = null,
            ),
        )

        // When
        val loaded = ConfigLoader().load(target)

        // Then
        assertNull(loaded.youtube.oauth2Token)
        assertEquals("", loaded.youtube.poToken)
        assertNull(loaded.youtube.visitorData)
        assertEquals("", loaded.youtube.remoteCipherUrl)
        assertNull(loaded.youtube.remoteCipherPassword)
    }

    @Test
    fun `Given two missing files When loaded by the same loader Then every load opens a fresh template stream`() {
        // Given
        val loader = ConfigLoader()
        val first = tempDir.resolve("first/config.json")
        val second = tempDir.resolve("second/config.json")

        // When
        assertFailsWith<EmptyDiscordTokenException> { loader.load(first) }
        assertFailsWith<EmptyDiscordTokenException> { loader.load(second) }

        // Then: a cached, already consumed stream would leave the second copy empty.
        assertEquals(templateText, Files.readString(first))
        assertEquals(templateText, Files.readString(second))
    }

    @Test
    fun `Given an unavailable template When a file is missing Then fail naming the resource without config values`() {
        // Given
        val target = tempDir.resolve("config.json")
        val loader = ConfigLoader(template = { null })

        // When
        val error = assertFailsWith<IllegalStateException> { loader.load(target) }

        // Then
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
        deezerEnabled: Boolean = false,
        deezerKey: String = "",
        oauth2Token: String? = null,
        poToken: String? = null,
        visitorData: String? = null,
        remoteCipherUrl: String? = null,
        remoteCipherPassword: String? = null,
    ): String = buildString {
        appendLine("{")
        appendLine("  \"discord_bot_token\": \"$token\",")
        appendLine("  \"debug\": false,")
        if (includeDatabase) {
            appendLine(
                "  \"database\": {\"enabled\": false, \"username\": \"db_user\", " +
                    "\"password\": \"db_password\", \"database\": \"db_name\"},",
            )
        }
        appendLine("  \"youtube\": {")
        appendLine("    \"enabled\": false,")
        appendLine("    \"oauth2_token\": ${quoted(oauth2Token)},")
        appendLine("    \"po_token\": ${quoted(poToken)},")
        appendLine("    \"visitor_data\": ${quoted(visitorData)},")
        appendLine("    \"remote_cipher_url\": ${quoted(remoteCipherUrl)},")
        appendLine("    \"remote_cipher_password\": ${quoted(remoteCipherPassword)}")
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

    private fun quoted(value: String?): String = value?.let { "\"$it\"" } ?: "null"

    private companion object {
        private const val SECRET_TOKEN = "loader-secret-token-sentinel"
        private const val HUGGING_PASSWORD = "loader-hugging-password-sentinel"
        private const val MALFORMED_JSON_SENTINEL = "malformed-json-secret-sentinel"
        private const val MALFORMED_JSON_FIXTURE = "/fixtures/malformed_config_with_secret.json"
    }
}
