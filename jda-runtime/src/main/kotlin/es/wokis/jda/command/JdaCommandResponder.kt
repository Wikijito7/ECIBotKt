package es.wokis.jda.command

import es.wokis.commands.neutral.CommandResponder
import es.wokis.commands.neutral.CommandResponse
import es.wokis.commands.neutral.ViewCommandResponder
import es.wokis.message.MessageView
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent

/**
 * JDA implementation of the neutral responder contracts.
 *
 * A mutex serializes REST actions through an explicit state machine: a state is committed only when
 * the delegate succeeds and the previous state is restored on failure, so a failed call never leaves
 * a false terminal state. After a successful defer the response edits the original message, and its
 * ephemeral flag is ignored because the defer fixed visibility. Text and composed views share the
 * same one-response budget per interaction.
 */
class JdaCommandResponder internal constructor(
    private val delegate: InteractionResponseDelegate,
) : CommandResponder, ViewCommandResponder {

    constructor(event: SlashCommandInteractionEvent) : this(JdaInteractionResponseDelegate(event))

    private val mutex = Mutex()
    private var state = ResponseState.IDLE

    override suspend fun defer(ephemeral: Boolean) {
        mutex.withLock {
            when (state) {
                ResponseState.IDLE -> Unit

                ResponseState.DEFERRING, ResponseState.DEFERRED ->
                    error("The interaction has already been deferred")

                ResponseState.RESPONDING, ResponseState.RESPONDED ->
                    error("The interaction has already been answered")
            }

            state = ResponseState.DEFERRING
            try {
                delegate.defer(ephemeral)
            } catch (error: Throwable) {
                state = ResponseState.IDLE
                throw error
            }
            state = ResponseState.DEFERRED
        }
    }

    override suspend fun respond(response: CommandResponse) {
        answer { previous ->
            if (previous == ResponseState.DEFERRED) {
                delegate.editOriginal(response.content)
            } else {
                delegate.reply(response.content, response.ephemeral)
            }
        }
    }

    override suspend fun respond(view: MessageView) {
        answer { previous ->
            if (previous == ResponseState.DEFERRED) {
                delegate.editOriginal(view)
            } else {
                delegate.reply(view, ephemeral = false)
            }
        }
    }

    private suspend fun answer(send: suspend (ResponseState) -> Unit) {
        mutex.withLock {
            val previous = state
            when (previous) {
                ResponseState.IDLE, ResponseState.DEFERRED -> Unit

                ResponseState.DEFERRING ->
                    error("The interaction is being deferred and cannot be answered yet")

                ResponseState.RESPONDING, ResponseState.RESPONDED ->
                    error("The interaction has already been answered")
            }

            state = ResponseState.RESPONDING
            try {
                send(previous)
            } catch (error: Throwable) {
                state = previous
                throw error
            }
            state = ResponseState.RESPONDED
        }
    }

    private enum class ResponseState {
        IDLE,
        DEFERRING,
        DEFERRED,
        RESPONDING,
        RESPONDED,
    }
}
