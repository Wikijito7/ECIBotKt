package es.wokis.commands.radio

import es.wokis.commands.neutral.ComponentContext
import es.wokis.commands.neutral.ComponentHandler
import es.wokis.commands.neutral.ComponentResponder
import es.wokis.message.ComponentId

/**
 * Neutral `/radio search` pagination for one [RadioSearchKind].
 *
 * The payload carries the page and the search term (`2::Rock`), so a "next" click pages forward
 * through the same results and a "previous" click pages back — the accepted
 * `RADIO_SEARCH_NAME_NEXT` fix. An old payload without a search term is acknowledged without an
 * edit rather than searching for nothing.
 */
class RadioSearchPaginationHandler(
    override val id: ComponentId,
    private val kind: RadioSearchKind,
    private val catalog: RadioCatalogPort,
    private val viewFactory: RadioMessageViewFactory,
) : ComponentHandler {

    private val forward: Boolean = id == kind.nextButton

    init {
        require(id == kind.previousButton || id == kind.nextButton) {
            "Radio search pagination handles only the previous/next ids of its own search kind"
        }
    }

    override suspend fun handle(context: ComponentContext, responder: ComponentResponder) {
        responder.deferEdit()
        val query = radioQueryFromPayload(context.payload) ?: return
        val targetPage = adjacentRadioPage(radioPageFromPayload(context.payload), forward)
        // The page bounds come from the catalog's returned page metadata (`currentPage`/`maxPage`),
        // not a local clamp: unlike the country-codes handler, this one never holds the full list.
        val page = catalog.search(kind, query, targetPage)
        responder.edit(
            viewFactory.createListPage(
                stations = page?.stations.orEmpty(),
                request = RadioPageRequest(
                    currentPage = page?.currentPage ?: FIRST_RADIO_PAGE,
                    pageCount = page?.maxPage ?: FIRST_RADIO_PAGE,
                    previous = kind.previousButton,
                    next = kind.nextButton,
                    query = query,
                ),
                guildId = context.guildId,
                guildDefaultLocale = context.guildLocale,
            ),
        )
    }
}
