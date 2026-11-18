package es.wokis.message

/** Gateway-neutral component ids shared with the Kord player surface. */
enum class ComponentId(val value: String) {
    QUEUE_PREVIOUS("queue_previous"),
    QUEUE_NEXT("queue_next"),
    PLAYER_RESUME("player_resume"),
    PLAYER_PAUSE("player_pause"),
    PLAYER_SKIP("player_skip"),
    PLAYER_DISCONNECT("player_disconnect"),
    PLAYER_SHUFFLE("player_shuffle"),
    PLAYER_RECONNECT("player_reconnect"),
    SOUNDS_PREVIOUS("sounds_previous"),
    SOUNDS_NEXT("sounds_next"),
    RADIO_LIST_NEXT("radio_list_next"),
    RADIO_LIST_PREVIOUS("radio_list_previous"),
    RADIO_SEARCH_NAME_NEXT("radio_search_name_next"),
    RADIO_SEARCH_NAME_PREVIOUS("radio_search_name_previous"),
    RADIO_SEARCH_COUNTRY_CODE_NEXT("radio_search_country_code_next"),
    RADIO_SEARCH_COUNTRY_CODE_PREVIOUS("radio_search_country_code_previous"),
    RADIO_COUNTRYCODES_NEXT("radio_countrycodes_next"),
    RADIO_COUNTRYCODES_PREVIOUS("radio_countrycodes_previous");

    /**
     * Wire custom id: the bare [value], plus `::<payload>` when this component carries state
     * (Kord's `radio_list_next::2` shape).
     */
    fun customId(payload: String? = null): String =
        if (payload.isNullOrEmpty()) value else "$value$SEPARATOR$payload"

    companion object {

        private const val SEPARATOR = "::"

        /** Resolves an id, ignoring the `::` payload suffix Kord appends to stateful components. */
        fun fromCustomId(customId: String): ComponentId? {
            val id = customId.substringBefore(SEPARATOR)
            return entries.firstOrNull { it.value == id }
        }

        /** Returns the `::` state payload of [customId], or null when it carries none. */
        fun payloadFrom(customId: String): String? =
            customId.substringAfter(SEPARATOR, "").takeIf(String::isNotEmpty)
    }
}
