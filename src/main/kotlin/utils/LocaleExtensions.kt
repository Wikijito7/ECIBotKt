package es.wokis.utils

import dev.kord.common.Locale
import dev.kord.common.entity.Snowflake
import es.wokis.domain.locale.DiscordLocaleCatalog
import es.wokis.domain.locale.LocaleCode
import es.wokis.domain.model.GuildId

/**
 * Kord edge adapter for the neutral domain types.
 *
 * All Kord locale/ID conversions live here so the domain, repository and
 * persistence layers stay free of Kord types.
 */

fun Locale?.orDefaultLocale(): Locale = this ?: Locale.ENGLISH_UNITED_STATES

fun Snowflake.toGuildId(): GuildId = GuildId(value)

/**
 * Map of canonical Discord locale tags to Kord Locale objects.
 *
 * The tags come from the neutral [DiscordLocaleCatalog], so entries that Kord itself does not
 * model in `Locale.ALL` — notably `ar` and `he` — are still selectable and persisting them is safe.
 */
val DISCORD_LOCALE_MAP: Map<String, Locale> = DiscordLocaleCatalog.supportedLocales.associate { localeCode ->
    localeCode.value to localeCode.toKordLocale()
}

/**
 * Converts a Kord [Locale] to its neutral [LocaleCode] representation.
 *
 * @throws IllegalArgumentException when the locale is not part of the canonical Discord catalog.
 */
fun Locale.toLocaleCode(): LocaleCode = toLocaleCodeOrNull()
    ?: throw IllegalArgumentException("Unknown locale: $this")

/**
 * Converts a Kord [Locale] to its neutral [LocaleCode] representation, or `null` when the locale
 * is not part of the canonical Discord catalog.
 */
fun Locale.toLocaleCodeOrNull(): LocaleCode? = DiscordLocaleCatalog.find(toDiscordTag())

/**
 * Converts a neutral [LocaleCode] to a Kord [Locale].
 *
 * The code must be a canonical catalog tag. This rejects format-valid but non-Discord tags such
 * as `xx-YY` instead of treating them as supported locales.
 *
 * @return the matching locale, or null when the stored code is not a known Discord locale
 */
fun LocaleCode.toKordLocaleOrNull(): Locale? = DiscordLocaleCatalog.find(value)?.toKordLocale()

private fun LocaleCode.toKordLocale(): Locale {
    val parts = value.split("-", limit = 2)
    return Locale(language = parts.first(), country = parts.getOrNull(1))
}

private fun Locale.toDiscordTag(): String = listOfNotNull(language, country).joinToString("-")
