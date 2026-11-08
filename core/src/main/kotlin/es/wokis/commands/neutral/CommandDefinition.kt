package es.wokis.commands.neutral

/**
 * Gateway-neutral definition of a command to publish.
 *
 * [name] and [description] are passed through unchanged to a gateway adapter, so they must
 * already satisfy the platform limits for a slash command. Keeping the definition neutral lets
 * the registration flow be exercised without a live gateway client.
 */
data class CommandDefinition(
    val name: String,
    val description: String,
)
