package es.wokis.commands.radio

import es.wokis.commands.neutral.ComponentContext
import es.wokis.commands.neutral.ComponentHandler
import es.wokis.commands.neutral.ComponentResponder
import es.wokis.message.ComponentId

/**
 * Neutral `/radio list` pagination.
 *
 * The page travels on the clicked button, so the target page is recomputed from the payload. One
 * instance exists per direction.
 */
class RadioListPaginationHandler(
    override val id: ComponentId,
    private val catalog: RadioCatalogPort,
    private val viewFactory: RadioMessageViewFactory,
) : ComponentHandler {

    private val forward: Boolean = id == ComponentId.RADIO_LIST_NEXT

    init {
        require(id == ComponentId.RADIO_LIST_PREVIOUS || id == ComponentId.RADIO_LIST_NEXT) {
            "Radio list pagination handles only the radio list previous/next ids"
        }
    }

    override suspend fun handle(context: ComponentContext, responder: ComponentResponder) {
        responder.deferEdit()
        val targetPage = adjacentRadioPage(radioPageFromPayload(context.payload), forward)
        // The page bounds come from the catalog's returned page metadata (`currentPage`/`maxPage`),
        // not a local clamp: unlike the country-codes handler, this one never holds the full list.
        val page = catalog.stationPage(targetPage)
        responder.edit(
            viewFactory.createListPage(
                stations = page?.stations.orEmpty(),
                request = RadioPageRequest(
                    currentPage = page?.currentPage ?: FIRST_RADIO_PAGE,
                    pageCount = page?.maxPage ?: FIRST_RADIO_PAGE,
                    previous = ComponentId.RADIO_LIST_PREVIOUS,
                    next = ComponentId.RADIO_LIST_NEXT,
                ),
                guildId = context.guildId,
                guildDefaultLocale = context.guildLocale,
            ),
        )
    }
}
