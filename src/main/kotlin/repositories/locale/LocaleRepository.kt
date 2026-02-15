package es.wokis.repositories.locale

import dev.kord.common.Locale
import dev.kord.common.entity.Snowflake

/**
 * Repository interface for managing guild locale preferences.
 * This abstraction allows for easy switching between different storage implementations
 * (e.g., local JSON file, database, API backend).
 */
interface LocaleRepository {

    /**
     * Retrieves the custom locale for a specific guild.
     *
     * @param guildId The guild's unique identifier
     * @return The custom locale if set, null otherwise
     */
    suspend fun getGuildLocale(guildId: Snowflake): Locale?

    /**
     * Sets a custom locale for a specific guild.
     *
     * @param guildId The guild's unique identifier
     * @param locale The locale to set
     */
    suspend fun setGuildLocale(guildId: Snowflake, locale: Locale)

    /**
     * Removes the custom locale for a specific guild, reverting to Discord's default.
     *
     * @param guildId The guild's unique identifier
     */
    suspend fun removeGuildLocale(guildId: Snowflake)

    /**
     * Retrieves all guild locale mappings.
     *
     * @return Map of guild IDs to their custom locales
     */
    suspend fun getAllGuildLocales(): Map<Snowflake, Locale>
}
