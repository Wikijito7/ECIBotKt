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
val DISCORD_LOCALE_MAP = Locale.ALL.associateBy {
    listOfNotNull(it.language, it.country).joinToString("-")
}

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
