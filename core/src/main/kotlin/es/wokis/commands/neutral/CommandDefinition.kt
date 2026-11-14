package es.wokis.commands.neutral

import es.wokis.domain.locale.LocaleCode

/**
 * Gateway-neutral definition of a command to publish.
 *
 * [name] and [description] are passed through unchanged to a gateway adapter, so they must
 * already satisfy the platform limits for a slash command. Keeping the definition neutral lets
 * the registration flow be exercised without a live gateway client.
 *
 * [adminOnly] and [stringOption] exist only for the narrow command shapes the migration needs;
 * they are not a generic command builder. [descriptionLocalizations] and
 * [StringOptionDefinition.descriptionLocalizations] carry only the translations the catalog
 * actually provides, keyed by canonical neutral tags.
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
