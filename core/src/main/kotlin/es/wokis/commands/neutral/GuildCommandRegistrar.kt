package es.wokis.commands.neutral

import es.wokis.domain.model.GuildId

/**
 * Publishes one [CommandDefinition] into [guildId] only — one guild per call, never globally, and an
 * unavailable guild fails instead of falling back, so nothing leaks to the global scope.
 */
fun interface GuildCommandRegistrar {

    suspend fun register(guildId: GuildId, command: CommandDefinition)
}
