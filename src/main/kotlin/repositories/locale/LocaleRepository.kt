package es.wokis.repositories.locale

import es.wokis.domain.locale.LocaleCode
import es.wokis.domain.model.GuildId

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
     * @return The custom locale code if set, null otherwise
     */
    suspend fun getGuildLocale(guildId: GuildId): LocaleCode?

    /**
     * Sets a custom locale for a specific guild.
     *
     * @param guildId The guild's unique identifier
     * @param locale The locale code to set
     */
    suspend fun setGuildLocale(guildId: GuildId, locale: LocaleCode)

    /**
     * Removes the custom locale for a specific guild, reverting to Discord's default.
     *
     * @param guildId The guild's unique identifier
     */
    suspend fun removeGuildLocale(guildId: GuildId)

    /**
     * Retrieves all guild locale mappings.
     *
     * @return Map of guild IDs to their custom locale codes
     */
    suspend fun getAllGuildLocales(): Map<GuildId, LocaleCode>
}
