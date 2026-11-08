package es.wokis.jda.command

import es.wokis.commands.neutral.CommandHandler
import es.wokis.commands.neutral.CommandResponder
import es.wokis.commands.neutral.CommandResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import org.slf4j.LoggerFactory

/**
 * Coroutine-safe JDA event router for neutral [CommandHandler]s.
 *
 * The gateway callback only identifies the handler and launches a child coroutine; all
 * handler and REST work runs off the JDA event thread. Each invocation gets its own
 * responder, so the one-defer/one-response contract is enforced per interaction.
 *
 * The router receives every slash-command event, including names it has no handler for, so an
 * unknown name is answered once with a generic ephemeral response instead of leaving the
 * interaction unacknowledged.
 *
 * The router owns [scope] and cancels it on [close].
 */
class JdaCommandRouter(
    handlers: List<CommandHandler>,
    private val contextMapper: JdaCommandContextMapper = JdaCommandContextMapper(),
    private val responderFactory: (SlashCommandInteractionEvent) -> CommandResponder = { JdaCommandResponder(it) },
    private val scope: CoroutineScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + CoroutineName("jda-command-router"),
    ),
) : ListenerAdapter(), AutoCloseable {

    private val handlers: Map<String, CommandHandler>

    init {
        require(handlers.map { it.name }.toSet().size == handlers.size) {
            "Command handler names must be unique"
        }
        this.handlers = handlers.associateBy { it.name }
    }

    override fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) {
        val handler = handlers[event.name]
        scope.launch {
            if (handler == null) {
                respondUnsupportedCommand(event)
                return@launch
            }
            val responder = responderFactory(event)
            try {
                handler.handle(contextMapper.map(event), responder)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                LOG.error("Command /{} failed", event.name, error)
                try {
                    responder.respond(CommandResponse(GENERIC_FAILURE_MESSAGE, ephemeral = true))
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (responseError: Exception) {
                    LOG.warn(
                        "Could not send the failure response for /{} (the interaction may already be answered)",
                        event.name,
                        responseError,
                    )
                }
            }
        }
    }

    /**
     * Answers a slash command that has no registered handler.
     *
     * The interaction is acknowledged exactly once with an ephemeral generic response. A failed
     * gateway call is logged and never escapes: this runs in a launched coroutine, where it would
     * otherwise surface only as an uncaught coroutine exception. Cancellation is rethrown and
     * fatal [Error]s are not caught.
     */
    private suspend fun respondUnsupportedCommand(event: SlashCommandInteractionEvent) {
        try {
            responderFactory(event).respond(CommandResponse(UNSUPPORTED_COMMAND_MESSAGE, ephemeral = true))
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            LOG.warn(
                "Could not reply to unsupported command /{} (the interaction may already be answered)",
                event.name,
                error,
            )
        }
    }

    override fun close() {
        scope.cancel()
    }

    private companion object {
        private val LOG = LoggerFactory.getLogger(JdaCommandRouter::class.java)
        private const val GENERIC_FAILURE_MESSAGE = "The command failed. Please try again later."
        private const val UNSUPPORTED_COMMAND_MESSAGE = "This command is not supported."
    }
}
