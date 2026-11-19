package es.wokis.commands.sounds

import es.wokis.commands.neutral.ComponentContext
import es.wokis.commands.neutral.ComponentHandler
import es.wokis.commands.neutral.ComponentResponder
import es.wokis.commands.player.adjacentPage
import es.wokis.commands.player.pageFromPayload
import es.wokis.commands.sound.SoundsCatalogPort
import es.wokis.message.ComponentId

/**
 * Neutral `/sounds` pagination.
 *
 * The page travels in the button payload (`sounds_next::2`), so the target page is recomputed from
 * the clicked button. One instance exists per direction.
 */
class SoundPaginationHandler(
    override val id: ComponentId,
    private val soundsCatalog: SoundsCatalogPort,
    private val viewFactory: SoundsMessageViewFactory,
) : ComponentHandler {

    private val forward: Boolean = id == ComponentId.SOUNDS_NEXT

    init {
        require(id == ComponentId.SOUNDS_PREVIOUS || id == ComponentId.SOUNDS_NEXT) {
            "Sound pagination handles only the sounds previous/next ids"
        }
    }

    override suspend fun handle(context: ComponentContext, responder: ComponentResponder) {
        responder.deferEdit()
        val soundNames = soundsCatalog.soundNames()
        val pageCount = soundPages(soundNames).size
        val page = adjacentPage(pageFromPayload(context.payload), forward, pageCount)
        responder.edit(
            viewFactory.createView(
                soundNames = soundNames,
                page = page,
                guildId = context.guildId,
                guildDefaultLocale = context.guildLocale,
            ),
        )
    }
}
