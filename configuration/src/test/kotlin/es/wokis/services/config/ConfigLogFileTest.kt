package es.wokis.services.config

import kotlinx.serialization.descriptors.elementNames
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConfigLogFileTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `Given a configured log_file When decoded Then the configured path is parsed`() {
        val decoded = decode(templateWith(LOG_PATH))

        assertEquals(LOG_PATH, decoded.logFile)
    }

    @Test
    fun `Given a legacy document without log_file When decoded Then the documented default applies`() {
        val decoded = decode(templateWithoutLogFile())

        assertEquals(DEFAULT_LOG_FILE, decoded.logFile)
    }

    @Test
    fun `Given an empty log_file When decoded Then it stays empty so the tee can be disabled`() {
        val decoded = decode(templateWith(""))

        assertEquals("", decoded.logFile)
    }

    @Test
    fun `Given a legacy document When decoded strictly Then the missing key is not a decoding failure`() {
        val strictJson = Json { ignoreUnknownKeys = false }
        val legacyText = templateWithoutLogFile().toString()

        val decoded = strictJson.decodeFromString(Config.serializer(), legacyText)

        assertEquals(DEFAULT_LOG_FILE, decoded.logFile)
    }

    @Test
    fun `Given a configured or blank log_file When rendered Then only the presence flag is shown`() {
        val configured = decode(templateWith(LOG_PATH)).toString()
        val blank = decode(templateWith("")).toString()

        assertTrue(configured.contains("logFileSet=true"), configured)
        assertFalse(configured.contains(LOG_PATH), configured)
        assertTrue(blank.contains("logFileSet=false"), blank)
    }

    @Test
    fun `Given the shared schema and template When inspected Then both declare the log_file key`() {
        val elementNames = Config.serializer().descriptor.elementNames

        assertTrue(elementNames.contains(LOG_KEY), "the schema must declare log_file: $elementNames")
        assertTrue(templateObject().containsKey(LOG_KEY), "the template must ship the log_file key")
    }

    @Test
    fun `Given a legacy document on disk without log_file When loaded Then the production loader applies the default`() {
        val target = writeConfig(loaderDocument(logFile = null))

        val loaded = ConfigLoader().load(target)

        assertEquals(DEFAULT_LOG_FILE, loaded.logFile)
    }

    @Test
    fun `Given an empty log_file on disk When loaded Then the production loader keeps it empty`() {
        val target = writeConfig(loaderDocument(logFile = ""))

        val loaded = ConfigLoader().load(target)

        assertEquals("", loaded.logFile)
    }

    private fun templateObject(): JsonObject = Json
        .parseToJsonElement(requireNotNull(javaClass.getResource(CONFIG_TEMPLATE_RESOURCE)).readText())
        .jsonObject

    private fun templateWith(logFile: String): JsonObject =
        JsonObject(templateObject() + (LOG_KEY to JsonPrimitive(logFile)))

    private fun templateWithoutLogFile(): JsonObject = JsonObject(templateObject() - LOG_KEY)

    /** A template-shaped document that also passes validation, optionally carrying `log_file`. */
    private fun loaderDocument(logFile: String?): String {
        val fields = templateObject()
            .filterKeys { key -> key != LOG_KEY }
            .toMutableMap()
        fields[DISCORD_BOT_TOKEN_KEY] = JsonPrimitive(LOADER_TOKEN)
        if (logFile != null) fields[LOG_KEY] = JsonPrimitive(logFile)
        return JsonObject(fields).toString()
    }

    private fun writeConfig(content: String): Path {
        val target = tempDir.resolve("config.json")
        Files.createDirectories(target.parent)
        Files.writeString(target, content)
        return target
    }

    private fun decode(document: JsonObject): Config = Json.decodeFromString(Config.serializer(), document.toString())

    private companion object {
        private const val LOG_KEY = "log_file"
        private const val DISCORD_BOT_TOKEN_KEY = "discord_bot_token"
        private const val DEFAULT_LOG_FILE = "./data/logs/jda-runtime.log"
        private const val LOG_PATH = "/tmp/config-log-file-sentinel.log"
        private const val LOADER_TOKEN = "config-log-file-loader-token-sentinel"
    }
}
