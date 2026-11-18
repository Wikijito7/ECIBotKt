package es.wokis.services.config

import es.wokis.exceptions.ConfigDecodingException
import es.wokis.exceptions.EmptyDeezerMasterDecryptionKeyException
import es.wokis.exceptions.EmptyDiscordTokenException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

const val DEFAULT_CONFIG_DIRECTORY: String = "./data"
const val DEFAULT_CONFIG_FILE_NAME: String = "config.json"
val DEFAULT_CONFIG_PATH: Path = Path.of(DEFAULT_CONFIG_DIRECTORY, DEFAULT_CONFIG_FILE_NAME)
const val CONFIG_TEMPLATE_RESOURCE: String = "/template/config_template.json"

/**
 * Loads the shared `config.json` for every composition root.
 *
 * Decoding is strict, and failures are reported through [ConfigDecodingException] without the
 * original decoder message or cause, which can embed raw JSON and leak secrets.
 */
class ConfigLoader(
    private val template: () -> InputStream? = ::openTemplate,
) {

    fun load(path: Path = DEFAULT_CONFIG_PATH): Config = load(path.toFile())

    fun load(file: File): Config {
        ensureExists(file)
        val config = decode(file)
        config.validate()
        return config
    }

    private fun decode(file: File): Config = try {
        STRICT_JSON.decodeFromString<Config>(file.readText())
    } catch (error: SerializationException) {
        // The cause is dropped on purpose: its message can embed the decoded input, i.e. the token.
        throw ConfigDecodingException(decodingFailureMessage(file, error))
    } catch (error: IllegalArgumentException) {
        // Some schema failures surface with the offending value in a plain IllegalArgumentException.
        throw ConfigDecodingException(decodingFailureMessage(file, error))
    }

    private fun ensureExists(file: File) {
        if (file.exists()) return
        file.absoluteFile.parentFile?.mkdirs()
        val templateStream = checkNotNull(template()) {
            "Missing configuration template resource '$CONFIG_TEMPLATE_RESOURCE'"
        }
        templateStream.use { Files.copy(it, file.toPath(), StandardCopyOption.REPLACE_EXISTING) }
    }

    private companion object {
        /** Not injectable, so no caller can enable lenient decoding. */
        private val STRICT_JSON: Json = Json
    }
}

fun Config.validate() {
    if (discordBotToken.isBlank()) {
        throw EmptyDiscordTokenException()
    }
    if (deezer.enabled && deezer.masterDecryptionKey.isEmpty()) {
        throw EmptyDeezerMasterDecryptionKeyException()
    }
}

private fun decodingFailureMessage(file: File, failure: Throwable): String =
    "Cannot decode the configuration at '${file.path}' " +
        "(phase: JSON decoding, failure: ${failure.javaClass.simpleName}). " +
        "The file is not valid for the required schema; its content is not included to keep secrets out of logs."

private fun openTemplate(): InputStream? =
    ConfigLoader::class.java.getResourceAsStream(CONFIG_TEMPLATE_RESOURCE)
