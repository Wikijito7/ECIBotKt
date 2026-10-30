package es.wokis.data.locale

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
 * Special value to indicate that the locale should be reset to Discord's default.
 */
const val RESET_LOCALE_VALUE = "reset"
