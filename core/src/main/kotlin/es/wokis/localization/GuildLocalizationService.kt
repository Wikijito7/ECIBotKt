package es.wokis.localization

import es.wokis.domain.locale.DiscordLocaleCatalog
import es.wokis.domain.locale.GetGuildLocaleUseCase
import es.wokis.domain.locale.LocaleCode
import es.wokis.domain.model.GuildId

/**
 * Gateway-neutral localization service shared by the Kord and JDA runtimes.
 *
 * Resolution order is the one the legacy service established:
 * 1. a persisted guild locale that belongs to the canonical catalog;
 * 2. the Discord guild default supplied by the adapter (never a user/client locale);
 * 3. the default `en-US` locale.
 *
 * A malformed or unknown stored value is ignored and falls through to the guild default. A valid
 * locale without a translation file is still selected, and [LocalizationCatalog] falls back per
 * key to the English string.
 */
class GuildLocalizationService(
    private val getGuildLocaleUseCase: GetGuildLocaleUseCase,
    private val catalog: LocalizationCatalog = LocalizationCatalog.load(),
) {

    suspend fun getString(
        key: String,
        guildId: GuildId? = null,
        guildDefaultLocale: LocaleCode? = null,
    ): String = catalog.getString(key, resolveLocale(guildId, guildDefaultLocale))

    suspend fun getStringFormat(
        key: String,
        guildId: GuildId? = null,
        guildDefaultLocale: LocaleCode? = null,
        vararg arguments: Any,
    ): String = getString(key, guildId, guildDefaultLocale).format(*arguments)

    fun getLocalizations(key: String): Map<LocaleCode, String> = catalog.getLocalizations(key)

    fun getDefaultString(key: String): String = catalog.getDefaultString(key)

    private suspend fun resolveLocale(guildId: GuildId?, guildDefaultLocale: LocaleCode?): LocaleCode {
        val persisted = guildId?.let { getGuildLocaleUseCase(it) }
        return persisted?.takeIf(DiscordLocaleCatalog::isSupported)
            ?: guildDefaultLocale?.takeIf(DiscordLocaleCatalog::isSupported)
            ?: DiscordLocaleCatalog.defaultLocale
    }
}
