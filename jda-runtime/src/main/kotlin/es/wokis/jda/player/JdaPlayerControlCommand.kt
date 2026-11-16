package es.wokis.jda.player

import es.wokis.commands.neutral.CommandContext
import es.wokis.commands.neutral.CommandDefinition
import es.wokis.commands.neutral.CommandHandler
import es.wokis.commands.neutral.CommandResponder
import es.wokis.commands.neutral.CommandResponse
import es.wokis.commands.neutral.GuildCommandRegistrar
import es.wokis.commands.neutral.StringOptionDefinition
import es.wokis.domain.model.GuildId

/** Administrator-only, guild-scoped `/jda-player` control command; the controller validates the action. */
internal class JdaPlayerControlCommand(
    private val controller: JdaPlayerCommandController,
) : CommandHandler {

    override val name: String = COMMAND_NAME

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
