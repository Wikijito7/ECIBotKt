package es.wokis.commands.neutral

import es.wokis.domain.locale.LocaleCode

/**
 * Gateway-neutral command to publish.
 *
 * [name] and [description] go to the gateway unchanged, so they must already satisfy its
 * slash-command limits. [adminOnly] and [stringOption] cover only the narrow migration shapes, not
 * a generic command builder. Localization maps carry only translations the catalog provides, keyed
 * by canonical tags.
 *
 * [subcommands] and [subcommandGroups] are optional and empty for a plain command, so the existing
 * single-option commands keep their exact shape. A gateway that publishes subcommands must not also
 * publish [stringOption].
 */
data class CommandDefinition(
    val name: String,
    val description: String,
    val adminOnly: Boolean = false,
    val stringOption: StringOptionDefinition? = null,
    val descriptionLocalizations: Map<LocaleCode, String> = emptyMap(),
    val subcommands: List<SubcommandDefinition> = emptyList(),
    val subcommandGroups: List<SubcommandGroupDefinition> = emptyList(),
) {
    init {
        require(stringOption == null || (subcommands.isEmpty() && subcommandGroups.isEmpty())) {
            "A command with subcommands or subcommand groups must not declare a top-level string option"
        }
    }
}

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

/**
 * One leaf subcommand of a group command: `/radio play` or the `name` leaf of `/radio search`.
 *
 * [stringOption] follows the same narrow single-string shape as [CommandDefinition.stringOption].
 */
data class SubcommandDefinition(
    val name: String,
    val description: String,
    val stringOption: StringOptionDefinition? = null,
    val descriptionLocalizations: Map<LocaleCode, String> = emptyMap(),
)

/**
 * One subcommand group of a group command: `/radio search name` publishes `search` as this group.
 *
 * The gateway-neutral model allows one nesting level only, mirroring both gateways' slash-command
 * limits: a group contains leaf subcommands and never another group.
 */
data class SubcommandGroupDefinition(
    val name: String,
    val description: String,
    val subcommands: List<SubcommandDefinition>,
    val descriptionLocalizations: Map<LocaleCode, String> = emptyMap(),
)
