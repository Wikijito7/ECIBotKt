package es.wokis.commands.skip

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
 * Neutral `/skip`.
 *
 * Kord parity: the caller must be in a voice channel, and the confirmation is sent even when
 * nothing was playing, because Kord's `skip()` has no idle guard.
 */
class SkipCommandHandler(
    private val actions: PlayerActionsPort,
    private val localizationService: GuildLocalizationService,
) : CommandHandler {

    override val name: String = PlayerCommandDefinitions.SKIP_COMMAND_NAME

    override suspend fun handle(context: CommandContext, responder: CommandResponder) {
        val actor = responder.requireVoiceActor(context, actions, localizationService) ?: return
        actions.skip(actor)
        responder.respondLocalized(localizationService, PlayerMessageKeys.SKIP_COMMAND_RESPONSE, context)
    }
}
