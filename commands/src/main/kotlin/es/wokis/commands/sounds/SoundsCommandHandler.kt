package es.wokis.commands.sounds

import es.wokis.commands.neutral.CommandContext
import es.wokis.commands.neutral.CommandHandler
import es.wokis.commands.neutral.CommandResponder
import es.wokis.commands.neutral.ViewCommandResponder
import es.wokis.commands.player.PlayerCommandDefinitions
import es.wokis.commands.sound.SoundsCatalogPort

private const val FIRST_PAGE = 1

/**
 * Neutral `/sounds`: renders Kord's paginated sound list for the first page.
 *
 * The reply is a composed view, so the adapter must supply a [ViewCommandResponder] as well.
 */
class SoundsCommandHandler(
    private val soundsCatalog: SoundsCatalogPort,
    private val viewFactory: SoundsMessageViewFactory,
) : CommandHandler {

    override val name: String = PlayerCommandDefinitions.SOUNDS_COMMAND_NAME

    override suspend fun handle(context: CommandContext, responder: CommandResponder) {
        check(responder is ViewCommandResponder) {
            "The /sounds reply is an embed, so the adapter must provide a view-capable responder"
        }
        responder.respond(
            viewFactory.createView(
                soundNames = soundsCatalog.soundNames(),
                page = FIRST_PAGE,
                guildId = context.guildId,
                guildDefaultLocale = context.guildLocale,
            ),
        )
    }
}
