package es.wokis.commands.radio

import es.wokis.commands.neutral.CommandContext
import es.wokis.commands.neutral.CommandHandler
import es.wokis.commands.neutral.CommandResponder
import es.wokis.commands.neutral.ViewCommandResponder
import es.wokis.message.ComponentId

/**
 * Neutral `/radio list`, mirroring Kord's `RadioListCommand`: the first page of the full station
 * catalog as a three-column paginated embed. A failed lookup renders the empty first page Kord
 * renders when `RemoteResponse.data` is null.
 */
class RadioListCommandHandler(
    private val catalog: RadioCatalogPort,
    private val viewFactory: RadioMessageViewFactory,
) : CommandHandler {

    override val name: String = RadioCommandDefinitions.LIST_COMMAND_NAME

    override suspend fun handle(context: CommandContext, responder: CommandResponder) {
        check(responder is ViewCommandResponder) { RADIO_VIEW_RESPONDER_REQUIRED }
        val page = catalog.stationPage(FIRST_RADIO_PAGE)
        responder.respond(
            viewFactory.createListPage(
                stations = page?.stations.orEmpty(),
                request = RadioPageRequest(
                    currentPage = FIRST_RADIO_PAGE,
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
