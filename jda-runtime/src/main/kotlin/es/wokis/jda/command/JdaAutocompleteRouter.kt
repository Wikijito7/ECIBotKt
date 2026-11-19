package es.wokis.jda.command

import es.wokis.commands.neutral.AutocompleteHandler
import es.wokis.commands.neutral.AutocompleteResponder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import org.slf4j.LoggerFactory

/**
 * Coroutine-safe JDA router for neutral [AutocompleteHandler]s.
 *
 * The command and focused option are resolved on the gateway callback and the handler runs off the
 * JDA event thread. An unknown command or option is logged and ignored, never acknowledged, so the
 * client simply sees no suggestions instead of a false reply.
 *
 * The router owns [scope] and cancels it on [close].
 */
class JdaAutocompleteRouter(
    handlers: List<AutocompleteHandler>,
    private val contextMapper: JdaAutocompleteContextMapper = JdaAutocompleteContextMapper(),
    private val responderFactory: (CommandAutoCompleteInteractionEvent) -> AutocompleteResponder = {
        JdaAutocompleteResponder(it)
    },
    private val scope: CoroutineScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + CoroutineName("jda-autocomplete-router"),
    ),
) : ListenerAdapter(), AutoCloseable {

    private val handlers: Map<String, Map<String, AutocompleteHandler>>

    init {
        require(handlers.map { it.commandName to it.optionName }.toSet().size == handlers.size) {
            "Autocomplete handler command/option pairs must be unique"
        }
        this.handlers = handlers
            .groupBy { it.commandName }
            .mapValues { (_, commandHandlers) -> commandHandlers.associateBy { it.optionName } }
    }

    override fun onCommandAutoCompleteInteraction(event: CommandAutoCompleteInteractionEvent) {
        val commandName = event.name
        val optionName = event.focusedOption.name
        val handler = handlers[commandName]?.get(optionName)
        if (handler == null) {
            LOG.warn("Ignoring autocomplete for '{}/{}' without a registered handler", commandName, optionName)
            return
        }
        scope.launch {
            try {
                handler.handle(contextMapper.map(event), responderFactory(event))
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                LOG.error("Autocomplete for '{}/{}' failed", commandName, optionName, error)
            }
        }
    }

    override fun close() {
        scope.cancel()
    }

    private companion object {
        private val LOG = LoggerFactory.getLogger(JdaAutocompleteRouter::class.java)
    }
}
