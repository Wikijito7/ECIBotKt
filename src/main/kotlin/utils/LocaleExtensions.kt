package es.wokis.utils

import dev.kord.common.Locale
import dev.kord.common.entity.Snowflake
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
 * Map of Discord locale codes to Kord Locale objects.
 * Source: https://discord.com/developers/docs/reference#locales
 */
val DISCORD_LOCALE_MAP: Map<String, Locale> = Locale.ALL.associateBy {
    listOfNotNull(it.language, it.country).joinToString("-")
}

/**
 * Reverse map to convert a Kord Locale back to its Discord locale code.
 */
private val LOCALE_TO_CODE_MAP: Map<Locale, String> = DISCORD_LOCALE_MAP.entries.associate { it.value to it.key }

/**
 * Converts a Kord [Locale] to its neutral [LocaleCode] representation.
 */
fun Locale.toLocaleCode(): LocaleCode = LocaleCode(toDiscordCode())

/**
 * Converts a neutral [LocaleCode] to a Kord [Locale].
 *
 * @return the matching locale, or null when the stored code is not a known Discord locale
 */
fun LocaleCode.toKordLocaleOrNull(): Locale? = try {
    Locale.fromString(value)
} catch (e: Exception) {
    Log.error("Failed to parse locale '$value'", e)
    null
}

private fun Locale.toDiscordCode(): String = LOCALE_TO_CODE_MAP[this]
    ?: throw IllegalArgumentException("Unknown locale: $this")
