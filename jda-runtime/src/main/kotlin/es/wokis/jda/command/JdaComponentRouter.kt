package es.wokis.jda.command

import es.wokis.commands.neutral.ComponentHandler
import es.wokis.commands.neutral.ComponentResponder
import es.wokis.message.ComponentId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import org.slf4j.LoggerFactory

/**
 * Coroutine-safe JDA button router for neutral [ComponentHandler]s.
 *
 * The gateway callback only resolves the id and launches a child coroutine, so handler and REST
 * work never runs on the JDA event thread. Unknown ids and clicks on messages without an embed are
 * ignored and logged, never acknowledged. Unknown ids and missing handlers are rejected on the
 * gateway callback, while the embed check runs inside the contained coroutine so that any JDA
 * access failure is caught by the same containment as the handler.
 *
 * The router owns [scope] and cancels it on [close].
 */
class JdaComponentRouter(
    handlers: List<ComponentHandler>,
    private val contextMapper: JdaComponentContextMapper = JdaComponentContextMapper(),
    private val responderFactory: (ButtonInteractionEvent) -> ComponentResponder = { JdaComponentResponder(it) },
    private val scope: CoroutineScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + CoroutineName("jda-component-router"),
    ),
) : ListenerAdapter(), AutoCloseable {

    private val handlers: Map<ComponentId, ComponentHandler>

    init {
        require(handlers.map { it.id }.toSet().size == handlers.size) {
            "Component handler ids must be unique"
        }
        this.handlers = handlers.associateBy { it.id }
    }

    override fun onButtonInteraction(event: ButtonInteractionEvent) {
        val componentId = ComponentId.fromCustomId(event.componentId)
        if (componentId == null) {
            LOG.warn("Ignoring a button with unknown component id '{}'", event.componentId)
            return
        }
        val handler = handlers[componentId]
        if (handler == null) {
            LOG.warn("Ignoring button '{}' without a registered handler", event.componentId)
            return
        }
        scope.launch {
            try {
                if (event.message.embeds.isEmpty()) {
                    LOG.warn("Ignoring button '{}' because its message has no embed", event.componentId)
                    return@launch
                }
                handler.handle(contextMapper.map(event, componentId), responderFactory(event))
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                LOG.error("Button '{}' failed", event.componentId, error)
            }
        }
    }

    override fun close() {
        scope.cancel()
    }

    private companion object {
        private val LOG = LoggerFactory.getLogger(JdaComponentRouter::class.java)
    }
}
