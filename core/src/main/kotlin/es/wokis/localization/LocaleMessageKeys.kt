package es.wokis.localization

/**
 * Message keys used by the neutral `/locale` command.
 *
 * They mirror the keys generated for the root Kord runtime from the same `lang.yml` catalog, so
 * both adapters render the same text without making `:core` depend on the root source set.
 */
object LocaleMessageKeys {
    const val LOCALE_COMMAND_DESCRIPTION = "locale_command_description"
    const val LOCALE_COMMAND_INPUT_DESCRIPTION = "locale_command_input_description"
    const val LOCALE_COMMAND_SUCCESS = "locale_command_success"
    const val LOCALE_COMMAND_RESET_SUCCESS = "locale_command_reset_success"
    const val LOCALE_COMMAND_INVALID_LOCALE = "locale_command_invalid_locale"
    const val ERROR_NO_GUILD = "error_no_guild"
    const val ERROR_NO_CONTENT_PROVIDED = "error_no_content_provided"
}
