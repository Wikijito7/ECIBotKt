package es.wokis.commands.walkthrough

import es.wokis.commands.neutral.CommandContext
import es.wokis.commands.neutral.CommandDefinition
import es.wokis.commands.neutral.CommandHandler
import es.wokis.commands.neutral.CommandResponder
import es.wokis.commands.neutral.CommandResponse
import es.wokis.commands.neutral.GuildCommandRegistrar
import es.wokis.domain.model.GuildId

/**
 * Pure diagnostic handler proving the neutral command boundary is wired end to end.
 *
 * It performs no I/O beyond the neutral responder and returns a single ephemeral text
 * response built only from neutral identifiers.
 */
class WalkthroughCommand : CommandHandler {

    override val name: String = COMMAND_NAME

    /**
     * Gateway-neutral definition used by adapters to publish this command.
     */
    val definition: CommandDefinition = CommandDefinition(
        name = COMMAND_NAME,
        description = COMMAND_DESCRIPTION,
    )

    /**
     * Publishes this command into [guildId] through [registrar].
     *
     * There is deliberately no global registration variant.
     */
    suspend fun registerIn(registrar: GuildCommandRegistrar, guildId: GuildId) {
        registrar.register(guildId, definition)
    }

    override suspend fun handle(context: CommandContext, responder: CommandResponder) {
        responder.respond(
            CommandResponse(
                content = buildString {
                    append("JDA walkthrough OK")
                    append(" | guild=").append(context.guildId?.toString() ?: "direct-message")
                    append(" | channel=").append(context.channelId)
                    append(" | user=").append(context.userId)
                    append(" | locale=").append(context.locale?.toString() ?: "unknown")
                },
                ephemeral = true,
            ),
        )
    }

    companion object {
        const val COMMAND_NAME = "jda-walkthrough"
        const val COMMAND_DESCRIPTION = "Diagnostic command proving the neutral command boundary is wired"
    }
}
