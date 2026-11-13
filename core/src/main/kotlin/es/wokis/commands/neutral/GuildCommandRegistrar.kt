package es.wokis.commands.neutral

import es.wokis.domain.model.GuildId

/**
 * Publishes command definitions into exactly one guild.
 *
 * [guildId] is the only permitted destination: implementations must not register commands
 * globally and must not fall back to another guild when the destination is unavailable.
 * Gateway-specific registration stays in adapter modules so callers can inject a fake
 * registrar instead of logging in to a gateway.
 */
fun interface GuildCommandRegistrar {

    /**
     * Creates or updates [command] in [guildId].
     *
     * Registration is idempotent: an existing command with the same name is replaced.
     */
    suspend fun register(guildId: GuildId, command: CommandDefinition)
}
