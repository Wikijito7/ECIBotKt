package es.wokis.commands.player

import es.wokis.domain.model.GuildId

/** Supplies the per-guild message coordinator the player commands drive; null when the guild is unreachable. */
fun interface PlayerMessageCoordinators {

    fun coordinatorFor(guildId: GuildId): PlayerMessageCoordinator?
}
