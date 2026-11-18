package es.wokis.commands.neutral

import es.wokis.domain.locale.LocaleCode

/**
 * Gateway-neutral command to publish.
 *
 * [name] and [description] go to the gateway unchanged, so they must already satisfy its
 * slash-command limits. [adminOnly] and [stringOption] cover only the narrow migration shapes, not
 * a generic command builder. Localization maps carry only translations the catalog provides, keyed
 * by canonical tags.
 */
data class CommandDefinition(
    val name: String,
    val description: String,
    val adminOnly: Boolean = false,
    val stringOption: StringOptionDefinition? = null,
    val descriptionLocalizations: Map<LocaleCode, String> = emptyMap(),
)

/**
 * A single required or optional string option attached to a [CommandDefinition].
 */
data class StringOptionDefinition(
    val name: String,
    val description: String,
    val required: Boolean = false,
    val autocomplete: Boolean = false,
    val descriptionLocalizations: Map<LocaleCode, String> = emptyMap(),
)
