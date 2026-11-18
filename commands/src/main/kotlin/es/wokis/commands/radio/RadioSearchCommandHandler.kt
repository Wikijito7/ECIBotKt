package es.wokis.commands.radio

import es.wokis.commands.neutral.CommandContext
import es.wokis.commands.neutral.CommandHandler
import es.wokis.commands.neutral.CommandResponder
import es.wokis.commands.neutral.ViewCommandResponder

/**
 * Neutral `/radio search name` and `/radio search countrycode`, mirroring Kord's two search
 * commands: the first results page as the shared radio embed, with the query carried on the
 * pagination buttons so later pages repeat the same search.
 */
class RadioSearchCommandHandler(
    private val kind: RadioSearchKind,
    private val catalog: RadioCatalogPort,
    private val viewFactory: RadioMessageViewFactory,
) : CommandHandler {

    override val name: String = kind.optionName

    override suspend fun handle(context: CommandContext, responder: CommandResponder) {
        check(responder is ViewCommandResponder) { RADIO_VIEW_RESPONDER_REQUIRED }
        val query = context.stringOptions[kind.optionName].orEmpty()
        val page = catalog.search(kind, query, FIRST_RADIO_PAGE)
        responder.respond(
            viewFactory.createListPage(
                stations = page?.stations.orEmpty(),
                request = RadioPageRequest(
                    currentPage = FIRST_RADIO_PAGE,
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
