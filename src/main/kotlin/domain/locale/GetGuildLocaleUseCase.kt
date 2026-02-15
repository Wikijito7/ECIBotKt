package es.wokis.domain.locale

import dev.kord.common.Locale
import dev.kord.common.entity.Snowflake
import es.wokis.repositories.locale.LocalJsonLocaleRepository

/**
 * Use case for retrieving a guild's custom locale preference.
 * This use case abstracts the data retrieval logic and allows for easy
 * switching between different repository implementations.
 */
class GetGuildLocaleUseCase(
    private val localeRepository: LocalJsonLocaleRepository
) {

    /**
     * Gets the custom locale for a guild.
     * If no custom locale is set, returns null (caller should use Discord's default).
     *
     * @param guildId The guild's unique identifier
     * @return The custom locale if set, null otherwise
     */
    suspend operator fun invoke(guildId: Snowflake): Locale? =
        localeRepository.getGuildLocale(guildId)
}
