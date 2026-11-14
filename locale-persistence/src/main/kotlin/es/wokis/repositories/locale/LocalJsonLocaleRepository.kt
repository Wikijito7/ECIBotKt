package es.wokis.repositories.locale

import es.wokis.data.locale.GuildLocalesContainer
import es.wokis.domain.locale.LocaleCode
import es.wokis.domain.locale.LocaleRepository
import es.wokis.domain.model.GuildId
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.File

private const val DATA_PATH = "./data/"
private const val FILE_NAME = "guild_locales.json"
private val LOG = LoggerFactory.getLogger(LocalJsonLocaleRepository::class.java)

/**
 * Local JSON implementation of LocaleRepository.
 * Stores guild locale preferences in a JSON file with in-memory caching.
 * This implementation is suitable for single-instance deployments.
 *
 * The persisted format is unchanged:
 * `{ "guild_locales": { "decimal-id": "locale-code" } }`
 *
 * Raw stored values are never sanitized: [getGuildLocale] returns the persisted [LocaleCode]
 * exactly as written, and [getAllGuildLocales] preserves malformed values instead of replacing
 * them with a default. Validation and fallback happen in a higher layer.
 *
 * @param json JSON codec used to read/write the container
 * @param file file backing the guild locales. Tests can inject a temporary file to avoid
 * touching the real `./data/guild_locales.json` file
 */
class LocalJsonLocaleRepository(
    private val json: Json,
    private val file: File
) : LocaleRepository {

    /**
     * Production constructor. Resolves (and creates if missing) the default
     * `./data/guild_locales.json` file, preserving the original behavior.
     *
     * @param json JSON codec used to read/write the container
     */
    constructor(json: Json) : this(json, getOrCreateFile(DATA_PATH, FILE_NAME))

    private val mutex = Mutex()
    private val guildLocalesMap: MutableMap<String, String> = mutableMapOf()

    init {
        loadFromFile()
    }

    /**
     * Retrieves the custom locale for a specific guild.
     *
     * @param guildId The guild's unique identifier
     * @return The custom locale code if set, null otherwise
     */
    override suspend fun getGuildLocale(guildId: GuildId): LocaleCode? = mutex.withLock {
        guildLocalesMap[guildId.toString()]?.let(::LocaleCode)
    }

    /**
     * Sets a custom locale for a specific guild.
     *
     * @param guildId The guild's unique identifier
     * @param locale The locale code to set
     */
    override suspend fun setGuildLocale(guildId: GuildId, locale: LocaleCode) = mutex.withLock {
        try {
            guildLocalesMap[guildId.toString()] = locale.value
            writeToFile()
            LOG.info("Set locale for guild {} to {}", guildId, locale.value)
        } catch (e: Exception) {
            LOG.error("Failed to set locale for guild $guildId", e)
            throw e
        }
    }

    /**
     * Removes the custom locale for a specific guild, reverting to Discord's default.
     *
     * @param guildId The guild's unique identifier
     */
    override suspend fun removeGuildLocale(guildId: GuildId) = mutex.withLock {
        try {
            guildLocalesMap.remove(guildId.toString())
            writeToFile()
            LOG.info("Removed locale for guild {}", guildId)
        } catch (e: Exception) {
            LOG.error("Failed to remove locale for guild $guildId", e)
            throw e
        }
    }

    /**
     * Retrieves all guild locale mappings.
     *
     * @return Map of guild IDs to their custom locale codes
     */
    override suspend fun getAllGuildLocales(): Map<GuildId, LocaleCode> = mutex.withLock {
        guildLocalesMap.map { (guildId, localeCode) ->
            GuildId.fromDecimal(guildId) to LocaleCode(localeCode)
        }.toMap()
    }

    private fun loadFromFile() {
        if (!file.exists() || file.length() == 0L) {
            LOG.info("Guild locales file not found or empty, starting with empty map")
            return
        }

        try {
            val container = json.decodeFromString<GuildLocalesContainer>(file.readText())
            guildLocalesMap.putAll(container.guildLocales)
            LOG.info("Loaded {} guild locales from file", guildLocalesMap.size)
        } catch (e: Exception) {
            LOG.error("Failed to load guild locales from file, starting with empty map", e)
            // Continue with empty map
        }
    }

    private fun writeToFile() {
        try {
            val container = GuildLocalesContainer(guildLocalesMap)
            file.writeText(json.encodeToString(container))
        } catch (e: Exception) {
            LOG.error("Failed to write guild locales to file", e)
            throw e
        }
    }
}

/**
 * Minimal file helper kept with the shared adapter so it does not depend on the root runtime.
 */
private fun getOrCreateFile(parent: String, fileName: String): File {
    val file = File(parent, fileName)
    if (!file.exists()) {
        file.parentFile.mkdirs()
        file.createNewFile()
    }
    return file
}
