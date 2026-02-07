package es.wokis.repositories.locale

import dev.kord.common.Locale
import dev.kord.common.entity.Snowflake
import es.wokis.data.locale.GuildLocalesContainer
import es.wokis.data.locale.toDiscordCode
import es.wokis.utils.getOrCreateFile
import es.wokis.utils.updateFile
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import java.io.File

private const val DATA_PATH = "./data/"
private const val FILE_NAME = "guild_locales.json"

/**
 * Local JSON implementation of LocaleRepository.
 * Stores guild locale preferences in a JSON file.
 * This implementation is suitable for single-instance deployments.
 */
class LocalJsonLocaleRepository {

    private val file: File = getOrCreateFile(DATA_PATH, FILE_NAME, null)
    private val mutex = Mutex()
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    /**
     * Retrieves the custom locale for a specific guild.
     *
     * @param guildId The guild's unique identifier
     * @return The custom locale if set, null otherwise
     */
    suspend fun getGuildLocale(guildId: Snowflake): Locale? = mutex.withLock {
        val container = readContainer()
        val localeString = container.guildLocales[guildId.toString()]
        localeString?.let { Locale.fromString(it) }
    }

    /**
     * Sets a custom locale for a specific guild.
     *
     * @param guildId The guild's unique identifier
     * @param locale The locale to set
     */
    suspend fun setGuildLocale(guildId: Snowflake, locale: Locale) = mutex.withLock {
        val container = readContainer()
        container.guildLocales[guildId.toString()] = locale.toDiscordCode()
        writeContainer(container)
    }

    /**
     * Removes the custom locale for a specific guild, reverting to Discord's default.
     *
     * @param guildId The guild's unique identifier
     */
    suspend fun removeGuildLocale(guildId: Snowflake) = mutex.withLock {
        val container = readContainer()
        container.guildLocales.remove(guildId.toString())
        writeContainer(container)
    }

    /**
     * Retrieves all guild locale mappings.
     *
     * @return Map of guild IDs to their custom locales
     */
    suspend fun getAllGuildLocales(): Map<Snowflake, Locale> = mutex.withLock {
        val container = readContainer()
        container.guildLocales.mapKeys { Snowflake(it.key.toULong()) }
            .mapValues { Locale.fromString(it.value) }
    }

    private fun readContainer(): GuildLocalesContainer {
        return if (file.exists() && file.length() > 0) {
            try {
                json.decodeFromString(file.readText())
            } catch (e: Exception) {
                GuildLocalesContainer()
            }
        } else {
            GuildLocalesContainer()
        }
    }

    private fun writeContainer(container: GuildLocalesContainer) {
        file.updateFile(container)
    }
}
