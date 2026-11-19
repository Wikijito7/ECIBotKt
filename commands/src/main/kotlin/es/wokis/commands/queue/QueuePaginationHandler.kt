package es.wokis.commands.queue

import es.wokis.commands.neutral.ComponentContext
import es.wokis.commands.neutral.ComponentHandler
import es.wokis.commands.neutral.ComponentResponder
import es.wokis.commands.player.PlayerActionsPort
import es.wokis.commands.player.PlayerActor
import es.wokis.commands.player.QueueMessageViewFactory
import es.wokis.commands.player.adjacentPage
import es.wokis.commands.player.pageFromPayload
import es.wokis.commands.player.queuePages
import es.wokis.message.ComponentId

/**
 * Neutral `/queue` pagination.
 *
 * The page travels in the button payload (`queue_next::2`), so the target page is recomputed from
 * the clicked button instead of parsed out of the rendered footer. One instance exists per
 * direction, and an old message simply clamps to the queue it finds.
 */
class QueuePaginationHandler(
    override val id: ComponentId,
    private val actions: PlayerActionsPort,
    private val viewFactory: QueueMessageViewFactory,
) : ComponentHandler {

    private val forward: Boolean = id == ComponentId.QUEUE_NEXT

    init {
        require(id == ComponentId.QUEUE_PREVIOUS || id == ComponentId.QUEUE_NEXT) {
            "Queue pagination handles only the queue previous/next ids"
        }
    }

    override suspend fun handle(context: ComponentContext, responder: ComponentResponder) {
        responder.deferEdit()
        val guildId = context.guildId ?: return
        val actor = PlayerActor(
            guildId = guildId,
            userId = context.userId,
            channelId = context.channelId,
            guildLocale = context.guildLocale,
        )
        val queue = actions.snapshot(actor) ?: return
        val pageCount = queuePages(queue.snapshot.queue).size
        val page = adjacentPage(pageFromPayload(context.payload), forward, pageCount)
        responder.edit(viewFactory.createView(queue, page, guildId, context.guildLocale))
    }
}
