package es.wokis.commands.next

import es.wokis.commands.neutral.CommandContext
import es.wokis.commands.neutral.CommandHandler
import es.wokis.commands.neutral.CommandResponder
import es.wokis.commands.player.NextTrackResult
import es.wokis.commands.player.PlayerActionsPort
import es.wokis.commands.player.PlayerCommandDefinitions
import es.wokis.commands.player.isTrackUrl
import es.wokis.commands.player.requireVoiceActor
import es.wokis.commands.player.respondLocalized
import es.wokis.commands.player.respondLocalizedFormat
import es.wokis.commands.player.transformMonochromeToTidal
import es.wokis.localization.GuildLocalizationService
import es.wokis.localization.PlayerMessageKeys

/**
 * Neutral `/next`, mirroring Kord's four outcomes.
 *
 * A link is acknowledged with `searching_song` and loaded as the next track; a search term moves a
 * matching queued track (`next_track_moved`) or, with an empty queue (`next_empty_queue`) or no
 * match (`next_track_not_in_queue_trying_search`), falls back to a provider load.
 */
class NextCommandHandler(
    private val actions: PlayerActionsPort,
    private val localizationService: GuildLocalizationService,
) : CommandHandler {

    override val name: String = PlayerCommandDefinitions.NEXT_COMMAND_NAME

    override suspend fun handle(context: CommandContext, responder: CommandResponder) {
        val input = context.stringOptions[PlayerCommandDefinitions.NEXT_OPTION_TRACK]?.trim()
        if (input.isNullOrEmpty()) {
            responder.respondLocalizedFormat(
                localizationService,
                PlayerMessageKeys.ERROR_NO_CONTENT_PROVIDED,
                context,
                PlayerCommandDefinitions.NEXT_OPTION_TRACK,
            )
            return
        }

        val actor = responder.requireVoiceActor(context, actions, localizationService) ?: return
        if (input.isTrackUrl()) {
            responder.respondLocalized(localizationService, PlayerMessageKeys.SEARCHING_SONG, context)
            actions.loadNext(actor, input.transformMonochromeToTidal())
            return
        }

        when (val result = actions.moveNext(actor, input)) {
            NextTrackResult.QueueEmpty ->
                responder.respondLocalized(localizationService, PlayerMessageKeys.NEXT_EMPTY_QUEUE, context)

            is NextTrackResult.Moved -> responder.respondLocalizedFormat(
                localizationService,
                PlayerMessageKeys.NEXT_TRACK_MOVED,
                context,
                result.trackName,
            )

            NextTrackResult.NotInQueue -> {
                responder.respondLocalizedFormat(
                    localizationService,
                    PlayerMessageKeys.NEXT_TRACK_NOT_IN_QUEUE_TRYING_SEARCH,
                    context,
                    input,
                )
                actions.loadNext(actor, input)
            }
        }
    }
}
