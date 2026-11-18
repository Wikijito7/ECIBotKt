package es.wokis.commands.radio

import es.wokis.commands.neutral.CommandContext
import es.wokis.commands.neutral.CommandHandler
import es.wokis.commands.neutral.CommandResponder
import es.wokis.commands.player.AudioContentActionsPort
import es.wokis.commands.player.requireVoiceActor
import es.wokis.commands.player.respondLocalized
import es.wokis.localization.GuildLocalizationService
import es.wokis.localization.RadioMessageKeys

/**
 * Neutral `/radio random`, mirroring Kord's `RadioRandomCommand`.
 *
 * Kord answers `radio_random_searching` before the lookup and edits it to `radio_random_error` on
 * failure. The one-reply responder keeps the successful `searching` text and answers the error text
 * directly when the lookup fails.
 */
class RadioRandomCommandHandler(
    private val catalog: RadioCatalogPort,
    private val actions: AudioContentActionsPort,
    private val localizationService: GuildLocalizationService,
) : CommandHandler {

    override val name: String = RadioCommandDefinitions.RANDOM_COMMAND_NAME

    override suspend fun handle(context: CommandContext, responder: CommandResponder) {
        val actor = responder.requireVoiceActor(context, actions, localizationService) ?: return
        val station = catalog.randomStation()
        if (station == null) {
            responder.respondLocalized(localizationService, RadioMessageKeys.RADIO_RANDOM_ERROR, context)
            return
        }
        responder.respondLocalized(localizationService, RadioMessageKeys.RADIO_RANDOM_SEARCHING, context)
        actions.playRadio(
            actor = actor,
            radioName = station.name,
            streamUrl = station.streamUrl,
            faviconUrl = station.faviconUrl,
        )
    }
}
