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
 * Administrator-only, guild-scoped `/jda-play` player command.
 *
 * The interaction is deferred before the provider load, which can take long enough to expire.
 */
internal class JdaPlayCommand(
    private val controller: JdaPlayerCommandController,
) : CommandHandler {

    override val name: String = COMMAND_NAME

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
