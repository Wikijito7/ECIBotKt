package es.wokis.commands.disconnect

import es.wokis.commands.neutral.CommandContext
import es.wokis.commands.neutral.CommandHandler
import es.wokis.commands.neutral.CommandResponder
import es.wokis.commands.player.PlayerActionsPort
import es.wokis.commands.player.PlayerCommandDefinitions
import es.wokis.commands.player.requireVoiceActor
import es.wokis.commands.player.respondLocalized
import es.wokis.localization.GuildLocalizationService
import es.wokis.localization.PlayerMessageKeys

/**
 * Neutral `/disconnect`.
 *
 * Kord's own `/disconnect` only calls `stop()`. This handler calls `stop()` and then closes the
 * voice connection — the owner-confirmed disconnect semantics, which match the full Kord
 * `handleDisconnectEvent` behaviour — and then confirms.
 */
class DisconnectCommandHandler(
    private val actions: PlayerActionsPort,
    private val localizationService: GuildLocalizationService,
) : CommandHandler {

    override val name: String = PlayerCommandDefinitions.DISCONNECT_COMMAND_NAME

    override suspend fun handle(context: CommandContext, responder: CommandResponder) {
        val actor = responder.requireVoiceActor(context, actions, localizationService) ?: return
        actions.stop(actor)
        actions.disconnect(actor)
        responder.respondLocalized(localizationService, PlayerMessageKeys.DISCONNECT_COMMAND_RESPONSE, context)
    }
}
