package es.wokis.jda.player

import es.wokis.commands.neutral.CommandContext
import es.wokis.commands.neutral.ComponentContext
import es.wokis.commands.neutral.ComponentHandler
import es.wokis.commands.neutral.ComponentResponder
import es.wokis.commands.player.PlayerCommandDefinitions
import es.wokis.jda.command.toJdaId
import es.wokis.message.ComponentId
import kotlinx.coroutines.CancellationException
import net.dv8tion.jda.api.JDA
import org.slf4j.LoggerFactory

/** Applies one player button through the controller and edits the player embed in place. */
internal class JdaPlayerButtonHandler(
    override val id: ComponentId,
    private val jda: JDA,
    private val runtime: JdaPlayerRuntime,
    private val controller: JdaPlayerCommandController,
) : ComponentHandler {

    override suspend fun handle(context: ComponentContext, responder: ComponentResponder) {
        val guild = context.guildId?.let { jda.getGuildById(it.toJdaId()) }
        if (guild == null) {
            LOG.warn("Ignoring player button '{}' outside a known guild", id.value)
            return
        }
        responder.deferEdit()
        try {
            controller.applyButton(context.toCommandContext(), id)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            LOG.warn("The player button '{}' action failed", id.value, error)
        }
        responder.edit(runtime.messagesFor(guild).playerView(context.guildLocale))
    }

    private companion object {
        private val LOG = LoggerFactory.getLogger(JdaPlayerButtonHandler::class.java)
    }
}

/** Kord player button ids in embed order. */
internal val PLAYER_BUTTON_IDS: List<ComponentId> = listOf(
    ComponentId.PLAYER_PAUSE,
    ComponentId.PLAYER_RESUME,
    ComponentId.PLAYER_SKIP,
    ComponentId.PLAYER_SHUFFLE,
    ComponentId.PLAYER_RECONNECT,
    ComponentId.PLAYER_DISCONNECT,
)

private fun ComponentContext.toCommandContext(): CommandContext = CommandContext(
    commandName = PlayerCommandDefinitions.PLAYER_COMMAND_NAME,
    guildId = guildId,
    channelId = channelId,
    userId = userId,
    locale = locale,
    guildLocale = guildLocale,
    userLocale = userLocale,
)
