package es.wokis.data.locale

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * JSON container for all guild locale mappings. The legacy persisted shape is preserved exactly:
 * `{ "guild_locales": { "decimal-id": "canonical-tag" } }`
 */
@Serializable
data class GuildLocalesContainer(
    @SerialName("guild_locales")
    val guildLocales: MutableMap<String, String> = mutableMapOf()
)
