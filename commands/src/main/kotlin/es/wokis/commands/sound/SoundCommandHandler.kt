package es.wokis.commands.sound

import es.wokis.commands.neutral.CommandContext
import es.wokis.commands.neutral.CommandHandler
import es.wokis.commands.neutral.CommandResponder
import es.wokis.commands.player.AudioContentActionsPort
import es.wokis.commands.player.PlayerCommandDefinitions
import es.wokis.commands.player.requireVoiceActor
import es.wokis.commands.player.respondLocalized
import es.wokis.commands.player.respondLocalizedFormat
import es.wokis.localization.GuildLocalizationService
import es.wokis.localization.PlayerMessageKeys

/**
 * Neutral `/sound`, mirroring Kord's play-by-name flow.
 *
 * Intentional deviation from Kord's two-reply miss (searching_song, then no_matches): a single-reply adapter answers no_matches alone.
 * The reply is `searching_song` once the name resolves to a playable sound; a missing or blank name
 * answers the matching localized error instead.
 */
class SoundCommandHandler(
    private val actions: AudioContentActionsPort,
    private val soundsCatalog: SoundsCatalogPort,
    private val localizationService: GuildLocalizationService,
) : CommandHandler {

    override val name: String = PlayerCommandDefinitions.SOUND_COMMAND_NAME

    override suspend fun handle(context: CommandContext, responder: CommandResponder) {
        val soundName = context.stringOptions[PlayerCommandDefinitions.SOUND_OPTION_NAME]
            ?.takeIf(String::isNotEmpty)
        if (soundName == null) {
            responder.respondLocalizedFormat(
                localizationService,
                PlayerMessageKeys.ERROR_NO_CONTENT_PROVIDED,
                context,
                PlayerCommandDefinitions.SOUND_OPTION_NAME,
            )
            return
        }

        val actor = responder.requireVoiceActor(context, actions, localizationService) ?: return
        if (!soundsCatalog.soundExists(soundName)) {
            responder.respondLocalizedFormat(
                localizationService,
                PlayerMessageKeys.NO_MATCHES,
                context,
                soundName,
            )
            return
        }

        responder.respondLocalized(localizationService, PlayerMessageKeys.SEARCHING_SONG, context)
        actions.playSound(actor, soundName)
    }
}
