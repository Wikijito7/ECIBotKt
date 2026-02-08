package es.wokis.data.locale

import dev.kord.common.Locale
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Container for all guild locale mappings.
 * Used for JSON serialization/deserialization.
 */
@Serializable
data class GuildLocalesContainer(
    @SerialName("guild_locales")
    val guildLocales: MutableMap<String, String> = mutableMapOf()
)

/**
 * Map of Discord locale codes to Locale objects.
 * Source: https://discord.com/developers/docs/reference#locales
 */
val DISCORD_LOCALE_MAP = mapOf(
    "da" to Locale.DANISH,
    "de" to Locale.GERMAN,
    "en-GB" to Locale.ENGLISH_GREAT_BRITAIN,
    "en-US" to Locale.ENGLISH_UNITED_STATES,
    "es-ES" to Locale.SPANISH_SPAIN,
    "fr" to Locale.FRENCH,
    "hr" to Locale.CROATIAN,
    "it" to Locale.ITALIAN,
    "lt" to Locale.LITHUANIAN,
    "hu" to Locale.HUNGARIAN,
    "nl" to Locale.DUTCH,
    "no" to Locale.NORWEGIAN,
    "pl" to Locale.POLISH,
    "pt-BR" to Locale.PORTUGUESE_BRAZIL,
    "ro" to Locale.ROMANIAN,
    "fi" to Locale.FINNISH,
    "sv-SE" to Locale.SWEDISH,
    "vi" to Locale.VIETNAMESE,
    "tr" to Locale.TURKISH,
    "cs" to Locale.CZECH,
    "el" to Locale.GREEK,
    "bg" to Locale.BULGARIAN,
    "ru" to Locale.RUSSIAN,
    "uk" to Locale.UKRAINIAN,
    "hi" to Locale.HINDI,
    "th" to Locale.THAI,
    "zh-CN" to Locale.CHINESE_CHINA,
    "ja" to Locale.JAPANESE,
    "zh-TW" to Locale.CHINESE_TAIWAN,
    "ko" to Locale.KOREAN
)

/**
 * List of all Discord-supported locales for the autocomplete feature.
 */
val DISCORD_SUPPORTED_LOCALES = DISCORD_LOCALE_MAP.values.toList()

/**
 * Reverse map to convert Locale back to its Discord locale code.
 */
val LOCALE_TO_CODE_MAP = DISCORD_LOCALE_MAP.entries.associate { it.value to it.key }

/**
 * Extension function to convert a Locale to its Discord code string.
 */
fun Locale.toDiscordCode(): String = LOCALE_TO_CODE_MAP[this]
    ?: throw IllegalArgumentException("Unknown locale: $this")

/**
 * Special value to indicate that the locale should be reset to Discord's default.
 */
const val RESET_LOCALE_VALUE = "reset"
