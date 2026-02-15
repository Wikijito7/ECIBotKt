package es.wokis.domain.locale

import dev.kord.common.Locale
import dev.kord.common.entity.Snowflake
import es.wokis.repositories.locale.LocalJsonLocaleRepository

/**
 * Use case for setting or removing a guild's custom locale preference.
 * This use case abstracts the data persistence logic and allows for easy
 * switching between different repository implementations.
 */
class SetGuildLocaleUseCase(
    private val localeRepository: LocalJsonLocaleRepository
) {

    /**
     * Sets a custom locale for a guild.
     *
     * @param guildId The guild's unique identifier
     * @param locale The locale to set
     */
    suspend operator fun invoke(guildId: Snowflake, locale: Locale) {
        localeRepository.setGuildLocale(guildId, locale)
    }

    /**
     * Removes the custom locale for a guild, reverting to Discord's default.
     *
     * @param guildId The guild's unique identifier
     */
    suspend fun removeLocale(guildId: Snowflake) {
        localeRepository.removeGuildLocale(guildId)
    }
}
