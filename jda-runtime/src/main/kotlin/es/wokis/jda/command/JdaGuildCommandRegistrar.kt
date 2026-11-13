package es.wokis.jda.command

import es.wokis.commands.neutral.CommandDefinition
import es.wokis.commands.neutral.GuildCommandRegistrar
import es.wokis.domain.model.GuildId
import es.wokis.jda.awaitRestActionCompletion
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.interactions.commands.build.Commands

/**
 * [GuildCommandRegistrar] backed by a live JDA client.
 *
 * The only registration route is the guild-scoped `guild.upsertCommand(...)` call on the guild
 * resolved from the validated [GuildId]. Commands are never published application-wide, so a
 * wrong guild fails fast instead of leaking commands to every guild the bot is in.
 */
internal class JdaGuildCommandRegistrar(
    private val jda: JDA,
) : GuildCommandRegistrar {

    override suspend fun register(guildId: GuildId, command: CommandDefinition) {
        val guild = jda.getGuildById(guildId.toJdaId())
            ?: error(
                "The bot is not a member of guild $guildId, so /${command.name} cannot be registered",
            )

        guild.upsertCommand(Commands.slash(command.name, command.description))
            .awaitRestActionCompletion()
    }
}

/**
 * Converts the unsigned neutral [GuildId] into JDA's signed 64-bit guild id.
 *
 * Discord snowflakes fit in the signed range in practice. A value above [Long.MAX_VALUE] is
 * rejected instead of wrapping around, because the wrapped id would silently address a
 * different guild and register the command there.
 */
internal fun GuildId.toJdaId(): Long {
    require(value <= Long.MAX_VALUE.toULong()) {
        "Guild id $value does not fit in JDA's signed 64-bit guild id range"
    }
    return value.toLong()
}
