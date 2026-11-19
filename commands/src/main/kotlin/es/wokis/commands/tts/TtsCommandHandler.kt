package es.wokis.commands.tts

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

/** Neutral `/tts`, mirroring Kord: the text is queued as generated speech. */
class TtsCommandHandler(
    private val actions: AudioContentActionsPort,
    private val localizationService: GuildLocalizationService,
) : CommandHandler {

    override val name: String = PlayerCommandDefinitions.TTS_COMMAND_NAME

    override suspend fun handle(context: CommandContext, responder: CommandResponder) {
        val message = context.stringOptions[PlayerCommandDefinitions.TTS_OPTION_MESSAGE]
            ?.takeIf(String::isNotEmpty)
        if (message == null) {
            responder.respondLocalizedFormat(
                localizationService,
                PlayerMessageKeys.ERROR_NO_CONTENT_PROVIDED,
                context,
                PlayerCommandDefinitions.TTS_OPTION_MESSAGE,
            )
            return
        }

        val actor = responder.requireVoiceActor(context, actions, localizationService) ?: return
        responder.respondLocalized(localizationService, PlayerMessageKeys.TTS_COMMAND_RESPONSE, context)
        actions.loadTts(actor, message)
    }
}
