package es.wokis.commands.radio

import es.wokis.commands.neutral.CommandContext
import es.wokis.commands.neutral.CommandHandler
import es.wokis.commands.neutral.CommandResponder
import es.wokis.commands.neutral.ViewCommandResponder
import es.wokis.commands.player.respondLocalized
import es.wokis.localization.GuildLocalizationService
import es.wokis.localization.RadioMessageKeys

/**
 * Neutral `/radio countrycodes`, mirroring Kord's `RadioCountryCodesCommand` error/empty branches
 * and flag-field embed.
 *
 * Beyond Kord, the embed is paginated and emits the `radio_countrycodes_previous`/`next` buttons the
 * accepted paired routing fix requires; a single page keeps Kord's exact output.
 */
class RadioCountryCodesCommandHandler(
    private val catalog: RadioCatalogPort,
    private val viewFactory: RadioMessageViewFactory,
    private val localizationService: GuildLocalizationService,
) : CommandHandler {

    override val name: String = RadioCommandDefinitions.COUNTRY_CODES_COMMAND_NAME

    override suspend fun handle(context: CommandContext, responder: CommandResponder) {
        val countryCodes = catalog.countryCodes()
        if (countryCodes == null) {
            responder.respondLocalized(localizationService, RadioMessageKeys.RADIO_COUNTRYCODES_ERROR, context)
            return
        }
        if (countryCodes.isEmpty()) {
            responder.respondLocalized(localizationService, RadioMessageKeys.RADIO_COUNTRYCODES_EMPTY, context)
            return
        }

        check(responder is ViewCommandResponder) { RADIO_VIEW_RESPONDER_REQUIRED }
        val pages = radioCountryCodePages(countryCodes)
        responder.respond(
            viewFactory.createCountryCodesPage(
                countryCodes = pages.first(),
                currentPage = FIRST_RADIO_PAGE,
                pageCount = pages.size,
                guildId = context.guildId,
                guildDefaultLocale = context.guildLocale,
            ),
        )
    }
}
