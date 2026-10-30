package es.wokis.domain.locale

import es.wokis.domain.model.GuildId

/**
 * Use case for retrieving a guild's custom locale preference.
 * This use case abstracts the data retrieval logic and allows for easy
 * switching between different repository implementations.
 */
class GetGuildLocaleUseCase(
    private val localeRepository: LocaleRepository
) {

    /**
     * Gets the custom locale for a guild.
     * If no custom locale is set, returns null (caller should use Discord's default).
     *
     * @param guildId The guild's unique identifier
     * @return The custom locale code if set, null otherwise
     */
    suspend operator fun invoke(guildId: GuildId): LocaleCode? =
        localeRepository.getGuildLocale(guildId)
}
