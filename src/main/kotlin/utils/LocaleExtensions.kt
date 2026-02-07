package es.wokis.utils

import dev.kord.common.Locale
import dev.kord.common.entity.Snowflake
import es.wokis.domain.locale.GetGuildLocaleUseCase

fun Locale?.orDefaultLocale() = this ?: Locale.ENGLISH_UNITED_STATES

/**
 * Resolves the effective locale for a guild.
 * First checks for a custom guild locale, then falls back to Discord's guild locale.
 *
 * @param guildId The guild's unique identifier
 * @param discordLocale The locale provided by Discord (may be null or default for non-Community guilds)
 * @param getGuildLocaleUseCase Use case for retrieving custom guild locale
 * @return The effective locale to use
 */
suspend fun resolveGuildLocale(
    guildId: Snowflake?,
    discordLocale: Locale?,
    getGuildLocaleUseCase: GetGuildLocaleUseCase
): Locale {
    return if (guildId != null) {
        getGuildLocaleUseCase(guildId) ?: discordLocale.orDefaultLocale()
    } else {
        discordLocale.orDefaultLocale()
    }
}
