package es.wokis.jda.player

import es.wokis.commands.neutral.CommandContext
import es.wokis.commands.neutral.CommandHandler
import es.wokis.commands.neutral.CommandResponder
import es.wokis.commands.neutral.CommandResponse
import es.wokis.commands.neutral.GuildCommandRegistrar
import es.wokis.commands.player.PlayerCommandDefinitions
import es.wokis.domain.model.GuildId
import es.wokis.localization.GuildLocalizationService

/** Public, guild-scoped `/player` control command; the controller validates the action. */
internal class JdaPlayerControlCommand(
    private val controller: JdaPlayerCommandController,
    private val localizationService: GuildLocalizationService,
) : CommandHandler {

    override val name: String = COMMAND_NAME

    suspend fun registerIn(registrar: GuildCommandRegistrar, guildId: GuildId) {
        registrar.register(guildId, PlayerCommandDefinitions.player(localizationService))
    }

    override suspend fun handle(context: CommandContext, responder: CommandResponder) {
        responder.defer(ephemeral = false)
        val action = context.stringOptions[OPTION_ACTION].orEmpty()
        responder.respond(CommandResponse(content = controller.control(context, action), ephemeral = false))
    }

    companion object {
        const val COMMAND_NAME = PlayerCommandDefinitions.PLAYER_COMMAND_NAME
        const val OPTION_ACTION = PlayerCommandDefinitions.PLAYER_OPTION_ACTION
    }
}
