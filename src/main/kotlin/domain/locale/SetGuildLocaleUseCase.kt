package es.wokis.domain.locale

import es.wokis.domain.model.GuildId
import es.wokis.repositories.locale.LocaleRepository

/**
 * Use case for setting or removing a guild's custom locale preference.
 * This use case abstracts the data persistence logic and allows for easy
 * switching between different repository implementations.
 */
class SetGuildLocaleUseCase(
    private val localeRepository: LocaleRepository
) {

    /**
     * Sets a custom locale for a guild.
     *
     * @param guildId The guild's unique identifier
     * @param locale The locale code to set
     */
    suspend operator fun invoke(guildId: GuildId, locale: LocaleCode) {
        localeRepository.setGuildLocale(guildId, locale)
    }

    /**
     * Removes a custom locale for a guild, reverting to Discord's default.
     *
     * @param guildId The guild's unique identifier
     */
    suspend fun removeLocale(guildId: GuildId) {
        localeRepository.removeGuildLocale(guildId)
    }
}
