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
 * Neutral `/radio play <name>`, mirroring Kord's `RadioPlayCommand`.
 *
 * Kord replies `searching_song` and then edits the same message with `radio_play_found` or
 * `radio_play_not_found`. The neutral responder allows one reply per invocation, so this handler
 * answers the final state directly; the voice/guild guard runs first exactly like Kord's
 * `getOrCreateLavaPlayerService`.
 */
class RadioPlayCommandHandler(
    private val catalog: RadioCatalogPort,
    private val actions: AudioContentActionsPort,
    private val localizationService: GuildLocalizationService,
) : CommandHandler {

    override val name: String = RadioCommandDefinitions.PLAY_COMMAND_NAME

    override suspend fun handle(context: CommandContext, responder: CommandResponder) {
        val actor = responder.requireVoiceActor(context, actions, localizationService) ?: return
        val radioName = context.stringOptions[RadioCommandDefinitions.PLAY_OPTION_NAME]
            ?.takeIf(String::isNotEmpty)
        if (radioName == null) {
            responder.respondLocalized(localizationService, RadioMessageKeys.RADIO_PLAY_REQUIRED, context)
            return
        }

        val station = catalog.findStation(radioName)
        if (station == null) {
            responder.respondLocalized(localizationService, RadioMessageKeys.RADIO_PLAY_NOT_FOUND, context)
            return
        }
        responder.respondLocalized(localizationService, RadioMessageKeys.RADIO_PLAY_FOUND, context)
        actions.playRadio(
            actor = actor,
            radioName = station.name,
            streamUrl = station.streamUrl,
            faviconUrl = station.faviconUrl,
        )
    }
}
