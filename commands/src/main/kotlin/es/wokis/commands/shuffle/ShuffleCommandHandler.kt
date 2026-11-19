package es.wokis.commands.shuffle

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
 * Neutral `/shuffle`.
 *
 * Kord parity: the caller must be in a voice channel, and the confirmation is sent even when the
 * queue did not change, because Kord's `shuffle()` has no empty-queue guard.
 */
class ShuffleCommandHandler(
    private val actions: PlayerActionsPort,
    private val localizationService: GuildLocalizationService,
) : CommandHandler {

    override val name: String = PlayerCommandDefinitions.SHUFFLE_COMMAND_NAME

    override suspend fun handle(context: CommandContext, responder: CommandResponder) {
        val actor = responder.requireVoiceActor(context, actions, localizationService) ?: return
        actions.shuffle(actor)
        responder.respondLocalized(localizationService, PlayerMessageKeys.SHUFFLE_COMMAND_RESPONSE, context)
    }
}
