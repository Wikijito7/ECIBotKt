package es.wokis.jda.player

import es.wokis.commands.neutral.CommandContext
import es.wokis.commands.neutral.CommandHandler
import es.wokis.commands.neutral.CommandResponder
import es.wokis.commands.neutral.CommandResponse
import es.wokis.commands.neutral.GuildCommandRegistrar
import es.wokis.commands.player.PlayerCommandDefinitions
import es.wokis.domain.model.GuildId
import es.wokis.localization.GuildLocalizationService

/**
 * Public, guild-scoped `/play`; the interaction is deferred before the provider load.
 */
internal class JdaPlayCommand(
    private val controller: JdaPlayerCommandController,
    private val localizationService: GuildLocalizationService,
) : CommandHandler {

    override val name: String = COMMAND_NAME

    suspend fun registerIn(registrar: GuildCommandRegistrar, guildId: GuildId) {
        registrar.register(guildId, PlayerCommandDefinitions.play(localizationService))
    }

    override suspend fun handle(context: CommandContext, responder: CommandResponder) {
        responder.defer(ephemeral = false)
        val input = context.stringOptions[OPTION_INPUT].orEmpty()
        responder.respond(CommandResponse(content = controller.play(context, input), ephemeral = false))
    }

    companion object {
        const val COMMAND_NAME = PlayerCommandDefinitions.PLAY_COMMAND_NAME
        const val OPTION_INPUT = PlayerCommandDefinitions.PLAY_OPTION_INPUT
    }
}
