package es.wokis.repositories.locale

import dev.kord.common.Locale
import dev.kord.common.entity.Snowflake
import es.wokis.data.locale.GuildLocalesContainer
import es.wokis.data.locale.toDiscordCode
import es.wokis.utils.Log
import es.wokis.utils.getOrCreateFile
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import java.io.File

private const val DATA_PATH = "./data/"
private const val FILE_NAME = "guild_locales.json"

/**
 * Local JSON implementation of LocaleRepository.
 * Stores guild locale preferences in a JSON file with in-memory caching.
 * This implementation is suitable for single-instance deployments.
 */
class LocalJsonLocaleRepository(
    private val json: Json
) {

    private val file: File = getOrCreateFile(DATA_PATH, FILE_NAME, null)
    private val mutex = Mutex()
    private val guildLocalesMap: MutableMap<String, String> = mutableMapOf()

    init {
        loadFromFile()
    }

    /**
     * Retrieves the custom locale for a specific guild.
     *
     * @param guildId The guild's unique identifier
     * @return The custom locale if set, null otherwise
     */
    suspend fun getGuildLocale(guildId: Snowflake): Locale? = mutex.withLock {
        guildLocalesMap[guildId.toString()]?.let { localeCode ->
            try {
                Locale.fromString(localeCode)
            } catch (e: Exception) {
                Log.error("Failed to parse locale '$localeCode' for guild $guildId", e)
                null
            }
        }
    }

    /**
     * Sets a custom locale for a specific guild.
     *
     * @param guildId The guild's unique identifier
     * @param locale The locale to set
     */
    suspend fun setGuildLocale(guildId: Snowflake, locale: Locale) = mutex.withLock {
        try {
            val localeCode = locale.toDiscordCode()
            guildLocalesMap[guildId.toString()] = localeCode
            writeToFile()
            Log.info("Set locale for guild $guildId to $localeCode")
        } catch (e: Exception) {
            Log.error("Failed to set locale for guild $guildId", e)
            throw e
        }
    }

    /**
     * Removes the custom locale for a specific guild, reverting to Discord's default.
     *
     * @param guildId The guild's unique identifier
     */
    suspend fun removeGuildLocale(guildId: Snowflake) = mutex.withLock {
        try {
            guildLocalesMap.remove(guildId.toString())
            writeToFile()
            Log.info("Removed locale for guild $guildId")
        } catch (e: Exception) {
            Log.error("Failed to remove locale for guild $guildId", e)
            throw e
        }
    }

    /**
     * Retrieves all guild locale mappings.
     *
     * @return Map of guild IDs to their custom locales
     */
    suspend fun getAllGuildLocales(): Map<Snowflake, Locale> = mutex.withLock {
        guildLocalesMap.mapKeys { Snowflake(it.key.toULong()) }
            .mapValues { (_, localeCode) ->
                try {
                    Locale.fromString(localeCode)
                } catch (e: Exception) {
                    Log.error("Failed to parse locale '$localeCode'", e)
                    Locale.ENGLISH_UNITED_STATES
                }
            }
    }

    private fun loadFromFile() {
        if (!file.exists() || file.length() == 0L) {
            Log.info("Guild locales file not found or empty, starting with empty map")
            return
        }

        try {
            val container = json.decodeFromString<GuildLocalesContainer>(file.readText())
            guildLocalesMap.putAll(container.guildLocales)
            Log.info("Loaded ${guildLocalesMap.size} guild locales from file")
        } catch (e: Exception) {
            Log.error("Failed to load guild locales from file, starting with empty map", e)
            // Continue with empty map
        }
    }

    private fun writeToFile() {
        try {
            val container = GuildLocalesContainer(guildLocalesMap)
            file.writeText(json.encodeToString(container))
        } catch (e: Exception) {
            Log.error("Failed to write guild locales to file", e)
            throw e
        }
    }
}
