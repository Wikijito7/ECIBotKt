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

/** Directory that holds the deployed shared configuration file. */
const val DEFAULT_CONFIG_DIRECTORY: String = "./data"

/** File name of the shared configuration inside [DEFAULT_CONFIG_DIRECTORY]. */
const val DEFAULT_CONFIG_FILE_NAME: String = "config.json"

/** Default shared configuration location: `./data/config.json`. */
val DEFAULT_CONFIG_PATH: Path = Path.of(DEFAULT_CONFIG_DIRECTORY, DEFAULT_CONFIG_FILE_NAME)

/** Classpath resource used to seed a missing configuration file. */
const val CONFIG_TEMPLATE_RESOURCE: String = "/template/config_template.json"

/**
 * Stateless loader for the shared JSON configuration.
 *
 * The loader is the single place that knows how `config.json` is read, so every composition root
 * (the root Kord runtime and the isolated JDA runtime) applies the same semantics:
 *
 * - **Default path.** [DEFAULT_CONFIG_PATH] (`./data/config.json`) unless the caller passes another
 *   [Path] or [File].
 * - **Create from template only when missing.** An existing file is never rewritten, reformatted or
 *   migrated; a missing one is seeded from [CONFIG_TEMPLATE_RESOURCE] so the first run can start
 *   from a documented skeleton. The template stream is opened fresh on every load, so a second load
 *   in the same process never consumes a closed stream.
 * - **Strict decoding.** An immutable default [Json] codec is used: unknown keys are rejected and
 *   every required field must be present, exactly as before this module existed. The codec is not
 *   injectable, so no caller can weaken strictness (for example with `ignoreUnknownKeys = true`).
 * - **Sanitized decoding failures.** A malformed or schema-incompatible file is reported through
 *   [ConfigDecodingException], which names the file, the decoding phase and the failure category
 *   only. That covers every decoder-originated [SerializationException] and the plain
 *   [IllegalArgumentException]s kotlinx-serialization raises for some schema problems.
 *   kotlinx-serialization messages may contain raw `JSON input:` excerpts, so neither the
 *   original message nor the original exception is propagated: configuration secrets never reach
 *   a log. I/O failures keep their OS-level message (a path, never file content) so they remain
 *   actionable.
 * - **Validation.** [Config.validate] runs after decoding, so an empty or whitespace-only Discord
 *   token, or a Deezer configuration enabled without its master decryption key, fails before any
 *   gateway is built.
 *
 * The class holds no mutable state and no reload/watch behaviour: configuration is read once when a
 * composition root starts, and changing it requires a restart.
 */
class ConfigLoader(
    private val template: () -> InputStream? = ::openTemplate,
) {

    /** Loads and validates the configuration at [path], seeding the template when it is missing. */
    fun load(path: Path = DEFAULT_CONFIG_PATH): Config = load(path.toFile())

    /** Loads and validates the configuration at [file], seeding the template when it is missing. */
    fun load(file: File): Config {
        ensureExists(file)
        val config = decode(file)
        config.validate()
        return config
    }

    /**
     * Decodes the strict schema without ever echoing the file or the serializer message.
     *
     * Validation deliberately runs in [load], outside this `try`, so explicit
     * `EmptyDiscordTokenException`/`EmptyDeezerMasterDecryptionKeyException` keep propagating as
     * their own types instead of being reported as decoding failures.
     */
    private fun decode(file: File): Config = try {
        STRICT_JSON.decodeFromString<Config>(file.readText())
    } catch (error: SerializationException) {
        // The exception type is reported so the failure is diagnosable, while the raw message is
        // dropped on purpose: it can embed the decoded input, i.e. the token and every secret.
        throw ConfigDecodingException(decodingFailureMessage(file, error))
    } catch (error: IllegalArgumentException) {
        // kotlinx-serialization reports a few schema problems as a plain IllegalArgumentException
        // instead of a SerializationException (SerializationException is itself an
        // IllegalArgumentException, so this branch only sees the others). Those messages can also
        // embed the offending value, so they are sanitized exactly like decoding failures and the
        // original exception is never chained.
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
        /**
         * Immutable strict codec shared by every load.
         *
         * The default [Json] instance rejects unknown keys and requires every non-default field,
         * and it is deliberately not configurable: injection would let a caller enable lenient
         * settings such as `ignoreUnknownKeys`, silently bypassing the required schema.
         */
        private val STRICT_JSON: Json = Json
    }
}

/**
 * Applies the required validation rules to the decoded configuration.
 *
 * Only the rules that already existed are enforced. The Discord token must be non-blank (a
 * whitespace-only token cannot authenticate and is treated like an empty one, without rewriting
 * it). Provider sections that are disabled keep their placeholder values, so a default template
 * remains valid; enabling a provider makes its required credentials mandatory.
 */
fun Config.validate() {
    if (discordBotToken.isBlank()) {
        throw EmptyDiscordTokenException()
    }
    if (deezer.enabled && deezer.masterDecryptionKey.isEmpty()) {
        throw EmptyDeezerMasterDecryptionKeyException()
    }
}

/**
 * Builds the only text a decoding failure may expose.
 *
 * The file path makes the failure actionable, the phase names what failed and the exception type
 * names the category without its message. Neither the raw input nor the original exception text is
 * interpolated, because a kotlinx-serialization message can contain a full `JSON input:` excerpt.
 */
private fun decodingFailureMessage(file: File, failure: Throwable): String =
    "Cannot decode the configuration at '${file.path}' " +
        "(phase: JSON decoding, failure: ${failure.javaClass.simpleName}). " +
        "The file is not valid for the required schema; its content is not included to keep secrets out of logs."

/**
 * Opens a fresh template stream for every load.
 *
 * The stream is deliberately not cached in a property: a cached `InputStream` is consumed and
 * closed by the first copy, so a second load in the same process would seed an empty file.
 */
private fun openTemplate(): InputStream? =
    ConfigLoader::class.java.getResourceAsStream(CONFIG_TEMPLATE_RESOURCE)
