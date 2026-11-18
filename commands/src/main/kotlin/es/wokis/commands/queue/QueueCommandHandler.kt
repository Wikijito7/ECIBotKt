package es.wokis.commands.queue

import es.wokis.commands.neutral.CommandContext
import es.wokis.commands.neutral.CommandHandler
import es.wokis.commands.neutral.CommandResponder
import es.wokis.commands.neutral.ViewCommandResponder
import es.wokis.commands.player.PlayerActionsPort
import es.wokis.commands.player.PlayerCommandDefinitions
import es.wokis.commands.player.QueueMessageViewFactory
import es.wokis.commands.player.requireVoiceActor
import es.wokis.commands.player.respondLocalized
import es.wokis.localization.GuildLocalizationService
import es.wokis.localization.PlayerMessageKeys

private const val FIRST_PAGE = 1

/**
 * Neutral `/queue`: renders Kord's paginated queue embed for the first page.
 *
 * The reply is a composed view, so the adapter must supply a [ViewCommandResponder] as well.
 */
class QueueCommandHandler(
    private val actions: PlayerActionsPort,
    private val localizationService: GuildLocalizationService,
    private val viewFactory: QueueMessageViewFactory,
) : CommandHandler {

    override val name: String = PlayerCommandDefinitions.QUEUE_COMMAND_NAME

    override suspend fun handle(context: CommandContext, responder: CommandResponder) {
        check(responder is ViewCommandResponder) {
            "The /queue reply is an embed, so the adapter must provide a view-capable responder"
        }
        val actor = responder.requireVoiceActor(context, actions, localizationService) ?: return
        val queue = actions.snapshot(actor)
        if (queue == null) {
            responder.respondLocalized(localizationService, PlayerMessageKeys.ERROR_NO_GUILD, context)
            return
        }
        responder.respond(viewFactory.createView(queue, FIRST_PAGE, context.guildId, context.guildLocale))
    }
}
