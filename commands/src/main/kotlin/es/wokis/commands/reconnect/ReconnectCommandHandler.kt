package es.wokis.commands.reconnect

import es.wokis.commands.neutral.CommandContext
import es.wokis.commands.neutral.CommandHandler
import es.wokis.commands.neutral.CommandResponder
import es.wokis.commands.player.PlayerActionsPort
import es.wokis.commands.player.PlayerCommandDefinitions
import es.wokis.commands.player.PlayerReconnectResult
import es.wokis.commands.player.requireVoiceActor
import es.wokis.commands.player.respondLocalized
import es.wokis.localization.GuildLocalizationService
import es.wokis.localization.PlayerMessageKeys

/**
 * Neutral `/reconnect`.
 *
 * Kord parity: it refuses with `reconnect_not_connected` when there is no session to rebuild, and
 * only confirms once the voice connection is restored with the current track and queue preserved.
 */
class ReconnectCommandHandler(
    private val actions: PlayerActionsPort,
    private val localizationService: GuildLocalizationService,
) : CommandHandler {

    override val name: String = PlayerCommandDefinitions.RECONNECT_COMMAND_NAME

    override suspend fun handle(context: CommandContext, responder: CommandResponder) {
        val actor = responder.requireVoiceActor(context, actions, localizationService) ?: return
        val key = when (actions.reconnect(actor)) {
            PlayerReconnectResult.RECONNECTED -> PlayerMessageKeys.RECONNECT_SUCCESS
            PlayerReconnectResult.NOT_CONNECTED -> PlayerMessageKeys.RECONNECT_NOT_CONNECTED
            // Kord has no message for a failed attempt; reuse the existing localized failure text.
            PlayerReconnectResult.FAILED -> PlayerMessageKeys.PLAYER_RECONNECT_FAILED
        }
        responder.respondLocalized(localizationService, key, context)
    }
}
