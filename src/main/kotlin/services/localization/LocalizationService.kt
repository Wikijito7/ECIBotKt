package es.wokis.services.localization

import dev.kord.common.Locale
import dev.kord.common.entity.Snowflake
import es.wokis.domain.locale.GetGuildLocaleUseCase
import es.wokis.localization.GuildLocalizationService
import es.wokis.localization.LocalizationCatalog
import es.wokis.utils.toGuildId
import es.wokis.utils.toKordLocaleOrNull
import es.wokis.utils.toLocaleCodeOrNull

/**
 * Kord-facing localization adapter.
 *
 * The catalog, fallback rules and guild-locale resolution live in the neutral
 * [GuildLocalizationService] in `:core`; this class only converts Kord [Snowflake] and [Locale]
 * values at the edge and keeps the legacy public signatures used by the root runtime.
 */
class LocalizationService(
    getGuildLocaleUseCase: GetGuildLocaleUseCase,
) {

    private val delegate = GuildLocalizationService(
        getGuildLocaleUseCase = getGuildLocaleUseCase,
        catalog = LocalizationCatalog.load(),
    )

    fun getLocalizations(key: String): MutableMap<Locale, String> =
        delegate.getLocalizations(key)
            .mapNotNull { (localeCode, value) -> localeCode.toKordLocaleOrNull()?.let { it to value } }
            .toMap()
            .toMutableMap()

    suspend fun getString(
        key: String,
        guildId: Snowflake? = null,
        discordLocale: Locale? = null,
    ): String = delegate.getString(
        key = key,
        guildId = guildId?.toGuildId(),
        guildDefaultLocale = discordLocale?.toLocaleCodeOrNull(),
    )

    suspend fun getStringFormat(
        key: String,
        guildId: Snowflake? = null,
        discordLocale: Locale? = null,
        vararg arguments: Any
    ): String = getString(key, guildId, discordLocale).format(*arguments)
}

/**
 * Compatibility aliases for the pre-extraction exception names used by the root test suite and
 * error-handling code. The actual exceptions are now owned by `:core`.
 */
typealias LocalizationFolderNotFoundException = es.wokis.localization.LocalizationFolderNotFoundException
typealias NoLocalizationFileFoundException = es.wokis.localization.NoLocalizationFileFoundException
typealias NoLocalizationFoundException = es.wokis.localization.NoLocalizationFoundException
