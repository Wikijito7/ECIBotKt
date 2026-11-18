package es.wokis.jda.command

import es.wokis.commands.neutral.ComponentResponder
import es.wokis.jda.awaitRestActionCompletion
import es.wokis.jda.player.JdaMessageViewMapper
import es.wokis.message.MessageView
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent

/** Narrow seam around the REST operations a component responder needs. */
internal interface ComponentResponseDelegate {

    suspend fun deferEdit()

    suspend fun editMessage(view: MessageView)

    suspend fun editOriginal(view: MessageView)
}

/**
 * JDA implementation of the neutral component responder contract.
 *
 * A mutex enforces at most one defer and one edit per interaction, and state only advances after
 * the REST call succeeds so a failure can be retried.
 */
class JdaComponentResponder internal constructor(
    private val delegate: ComponentResponseDelegate,
) : ComponentResponder {

    constructor(event: ButtonInteractionEvent) : this(JdaComponentResponseDelegate(event))

    private val mutex = Mutex()
    private var deferred = false
    private var edited = false

    override suspend fun deferEdit() {
        mutex.withLock {
            check(!deferred) { "The interaction has already been deferred" }
            check(!edited) { "The interaction has already been edited" }
            delegate.deferEdit()
            deferred = true
        }
    }

    override suspend fun edit(view: MessageView) {
        mutex.withLock {
            check(!edited) { "The interaction has already been edited" }
            if (deferred) {
                delegate.editOriginal(view)
            } else {
                delegate.editMessage(view)
            }
            edited = true
        }
    }
}

/** [ComponentResponseDelegate] backed by a real JDA button event. */
internal class JdaComponentResponseDelegate(
    private val event: ButtonInteractionEvent,
) : ComponentResponseDelegate {

    override suspend fun deferEdit() {
        event.deferEdit().awaitRestActionCompletion()
    }

    override suspend fun editMessage(view: MessageView) {
        event.editMessage(JdaMessageViewMapper.toEditData(view)).awaitRestActionCompletion()
    }

    override suspend fun editOriginal(view: MessageView) {
        event.hook.editOriginal(JdaMessageViewMapper.toEditData(view)).awaitRestActionCompletion()
    }
}
