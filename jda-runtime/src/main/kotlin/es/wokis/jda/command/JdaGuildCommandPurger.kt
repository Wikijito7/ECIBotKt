package es.wokis.jda.command

import net.dv8tion.jda.api.events.guild.GuildJoinEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import org.slf4j.LoggerFactory

private val LOG = LoggerFactory.getLogger("es.wokis.jda.command.JdaGuildCommandPurger")

/**
 * Empties the command set of a guild that joins after startup, so a late join cannot keep a
 * guild-scoped copy that shadows the global commands.
 */
internal class JdaGuildCommandPurger : ListenerAdapter() {

    override fun onGuildJoin(event: GuildJoinEvent) {
        event.guild.updateCommands().queue(
            { LOG.info("Purged guild-scoped commands in a guild that joined after startup") },
            { error ->
                LOG.warn(
                    "Could not purge guild-scoped commands in a guild that joined after startup",
                    error,
                )
            },
        )
    }
}
