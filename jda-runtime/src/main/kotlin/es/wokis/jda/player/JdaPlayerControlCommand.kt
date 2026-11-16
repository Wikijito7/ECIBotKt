package es.wokis.jda.player

import es.wokis.commands.neutral.CommandContext
import es.wokis.commands.neutral.CommandDefinition
import es.wokis.commands.neutral.CommandHandler
import es.wokis.commands.neutral.CommandResponder
import es.wokis.commands.neutral.CommandResponse
import es.wokis.commands.neutral.GuildCommandRegistrar
import es.wokis.commands.neutral.StringOptionDefinition
import es.wokis.domain.model.GuildId

/**
 * Minimal, administrator-only `/jda-player` control command for the isolated JDA player.
 *
 * Supported actions: `queue`, `pause`, `resume`, `skip`, `stop`, `disconnect` and `reconnect`. The
 * action is a plain string option (validated by the controller) so the existing neutral command
 * definition does not need a choice type. `disconnect` clears the queue by owner decision, while
 * `reconnect` preserves the current track and pending queue.
 *
 * The command is published only into the development guild and is only reachable by members with
 * the administrator permission; it never exposes configuration or secrets.
 */
internal class JdaPlayerControlCommand(
    private val controller: JdaPlayerCommandController,
) : CommandHandler {

    override val name: String = COMMAND_NAME

    /** Guild-scoped, administrator-only definition used by the JDA adapter to publish it. */
    val definition: CommandDefinition = CommandDefinition(
        name = COMMAND_NAME,
        description = COMMAND_DESCRIPTION,
        adminOnly = true,
        stringOption = StringOptionDefinition(
            name = OPTION_ACTION,
            description = OPTION_ACTION_DESCRIPTION,
            required = true,
        ),
    )

    /** Publishes this command into [guildId]; there is deliberately no global variant. */
    suspend fun registerIn(registrar: GuildCommandRegistrar, guildId: GuildId) {
        registrar.register(guildId, definition)
    }

    override suspend fun handle(context: CommandContext, responder: CommandResponder) {
        responder.defer(ephemeral = true)
        val action = context.stringOptions[OPTION_ACTION].orEmpty()
        val message = controller.control(context, action)
        responder.respond(CommandResponse(content = message, ephemeral = true))
    }

    companion object {
        const val COMMAND_NAME = "jda-player"
        const val COMMAND_DESCRIPTION = "Controls the isolated JDA player (admin only)"
        const val OPTION_ACTION = "action"
        const val OPTION_ACTION_DESCRIPTION = "queue, pause, resume, skip, stop, disconnect or reconnect"
    }
}
