package es.wokis.commands

enum class ComponentsEnum(val customId: String) {
    QUEUE_PREVIOUS("queue_previous"),
    QUEUE_NEXT("queue_next"),
    PLAYER_RESUME("player_resume"),
    PLAYER_PAUSE("player_pause"),
    PLAYER_SKIP("player_skip"),
    PLAYER_DISCONNECT("player_disconnect"),
    PLAYER_SHUFFLE("player_shuffle"),
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

    companion object {
        fun forCustomId(customId: String) = entries.find { it.customId == customId }
    }
}
