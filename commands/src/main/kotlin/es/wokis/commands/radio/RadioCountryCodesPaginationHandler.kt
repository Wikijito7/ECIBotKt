package es.wokis.commands.radio

import es.wokis.commands.neutral.ComponentContext
import es.wokis.commands.neutral.ComponentHandler
import es.wokis.commands.neutral.ComponentResponder
import es.wokis.message.ComponentId

/**
 * Neutral `/radio countrycodes` pagination — the accepted paired `RADIO_COUNTRYCODES_NEXT`/
 * `RADIO_COUNTRYCODES_PREVIOUS` fix.
 *
 * Kord declares both ids and never emits or routes them; this handler pages the country-code list
 * one page in the clicked direction, clamped inside the page bounds. A failed lookup acknowledges
 * the click without editing.
 */
class RadioCountryCodesPaginationHandler(
    override val id: ComponentId,
    private val catalog: RadioCatalogPort,
    private val viewFactory: RadioMessageViewFactory,
) : ComponentHandler {

    private val forward: Boolean = id == ComponentId.RADIO_COUNTRYCODES_NEXT

    init {
        require(
            id == ComponentId.RADIO_COUNTRYCODES_PREVIOUS || id == ComponentId.RADIO_COUNTRYCODES_NEXT,
        ) {
            "Radio country-code pagination handles only the country-code previous/next ids"
        }
    }

    override suspend fun handle(context: ComponentContext, responder: ComponentResponder) {
        responder.deferEdit()
        val countryCodes = catalog.countryCodes() ?: return
        val pages = radioCountryCodePages(countryCodes)
        if (pages.isEmpty()) return
        val page = adjacentRadioPage(radioPageFromPayload(context.payload), forward)
            .coerceAtMost(pages.size)
        responder.edit(
            viewFactory.createCountryCodesPage(
                countryCodes = pages[page - 1],
                currentPage = page,
                pageCount = pages.size,
                guildId = context.guildId,
                guildDefaultLocale = context.guildLocale,
            ),
        )
    }
}
