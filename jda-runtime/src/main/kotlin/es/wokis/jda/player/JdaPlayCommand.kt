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
 * Minimal, administrator-only `/jda-play` command for the isolated JDA real-player slice.
 *
 * It exists to enable the deferred Phase 3 staging matrix, not to port the production music
 * commands (that remains Phase 4 work). The command is published only into the development guild
 * and carries one required string option:
 *
 * - `input`: a track name, URL or playlist URL handed to the shared player engine.
 *
 * The handler rejects direct messages, defers the interaction before the provider load starts (a
 * load can take seconds; an unacknowledged interaction would expire), and replies ephemerally with
 * the adapter-local result.
 */
internal class JdaPlayCommand(
    private val controller: JdaPlayerCommandController,
) : CommandHandler {

    override val name: String = COMMAND_NAME

    /** Guild-scoped, administrator-only definition used by the JDA adapter to publish it. */
    val definition: CommandDefinition = CommandDefinition(
        name = COMMAND_NAME,
        description = COMMAND_DESCRIPTION,
        adminOnly = true,
        stringOption = StringOptionDefinition(
            name = OPTION_INPUT,
            description = OPTION_INPUT_DESCRIPTION,
            required = true,
        ),
    )

    /** Publishes this command into [guildId]; there is deliberately no global variant. */
    suspend fun registerIn(registrar: GuildCommandRegistrar, guildId: GuildId) {
        registrar.register(guildId, definition)
    }

    override suspend fun handle(context: CommandContext, responder: CommandResponder) {
        responder.defer(ephemeral = true)
        val input = context.stringOptions[OPTION_INPUT].orEmpty()
        val message = controller.play(context, input)
        responder.respond(CommandResponse(content = message, ephemeral = true))
    }

    companion object {
        const val COMMAND_NAME = "jda-play"
        const val COMMAND_DESCRIPTION = "Loads a track in the isolated JDA player and joins your voice channel"
        const val OPTION_INPUT = "input"
        const val OPTION_INPUT_DESCRIPTION = "Track name, URL or playlist URL to load"
    }
}
