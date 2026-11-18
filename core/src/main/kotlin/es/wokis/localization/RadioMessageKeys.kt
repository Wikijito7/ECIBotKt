package es.wokis.localization

/**
 * Message keys used by the neutral radio command surface.
 *
 * They use the shared `lang.yml` catalog in `:core`, the source of truth the root Kord
 * `LocalizationKeys` is generated from, so both adapters render the same text. Every key here
 * already exists in both locales; the radio group adds no new catalog entries.
 */
object RadioMessageKeys {
    const val RADIO_COMMAND_DESCRIPTION = "radio_command_description"
    const val RADIO_UNKNOWN_MESSAGE = "radio_unknown_message"

    const val RADIO_PLAY_COMMAND_DESCRIPTION = "radio_play_command_description"
    const val RADIO_PLAY_INPUT_DESCRIPTION = "radio_play_input_description"
    const val RADIO_PLAY_FOUND = "radio_play_found"
    const val RADIO_PLAY_NOT_FOUND = "radio_play_not_found"
    const val RADIO_PLAY_REQUIRED = "radio_play_required"

    const val RADIO_LIST_COMMAND_DESCRIPTION = "radio_list_command_description"
    const val RADIO_LIST_EMBED_TITLE = "radio_list_embed_title"
    const val RADIO_LIST_EMBED_DESCRIPTION = "radio_list_embed_description"

    const val RADIO_SEARCH_COMMAND_DESCRIPTION = "radio_search_command_description"
    const val RADIO_SEARCH_NAME_COMMAND_DESCRIPTION = "radio_search_name_command_description"
    const val RADIO_SEARCH_COUNTRYCODE_COMMAND_DESCRIPTION = "radio_search_countrycode_command_description"
    const val RADIO_SEARCH_NAME_INPUT_DESCRIPTION = "radio_search_name_input_description"
    const val RADIO_SEARCH_COUNTRYCODE_INPUT_DESCRIPTION = "radio_search_countrycode_input_description"

    const val RADIO_RANDOM_COMMAND_DESCRIPTION = "radio_random_command_description"
    const val RADIO_RANDOM_SEARCHING = "radio_random_searching"
    const val RADIO_RANDOM_ERROR = "radio_random_error"

    const val RADIO_COUNTRYCODES_COMMAND_DESCRIPTION = "radio_countrycodes_command_description"
    const val RADIO_COUNTRYCODES_EMBED_TITLE = "radio_countrycodes_embed_title"
    const val RADIO_COUNTRYCODES_EMPTY = "radio_countrycodes_empty"
    const val RADIO_COUNTRYCODES_ERROR = "radio_countrycodes_error"
}
