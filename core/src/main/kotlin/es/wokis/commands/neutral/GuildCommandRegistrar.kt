package es.wokis.commands.neutral

import es.wokis.domain.model.GuildId

/**
 * Publishes one [CommandDefinition] for [guildId] — one guild per call, and an unavailable guild
 * fails instead of falling back, so nothing leaks to the global scope.
 *
 * Implementors may also be guild-agnostic collectors that ignore [guildId] and publish the collected
 * definitions globally, as the JDA global-only runtime does.
 */
fun interface GuildCommandRegistrar {

    suspend fun register(guildId: GuildId, command: CommandDefinition)
}
