package es.wokis.jda.command

import es.wokis.commands.neutral.CommandResponder
import es.wokis.commands.neutral.CommandResponse
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent

/**
 * JDA implementation of the neutral responder contract.
 *
 * Every REST action is serialized and driven through an explicit state machine:
 * a state is committed only when the delegate operation succeeds, and the previous state is
 * restored when it fails. That way a rejected or failed `queue` never leaves a false terminal
 * state behind, so the router can safely attempt its fallback: a failed defer may still reply
 * immediately, and a failed edit may still retry the edit of the deferred original.
 *
 * As required by Discord, the deferred message is the original message; after a successful
 * defer the response edits it and the response ephemeral flag is deliberately ignored because
 * visibility was fixed by the defer call.
 */
class JdaCommandResponder internal constructor(
    private val delegate: InteractionResponseDelegate,
) : CommandResponder {

    constructor(event: SlashCommandInteractionEvent) : this(JdaInteractionResponseDelegate(event))

    private val mutex = Mutex()
    private var state = ResponseState.IDLE

    override suspend fun defer(ephemeral: Boolean) {
        mutex.withLock {
            when (state) {
                ResponseState.IDLE -> Unit

                ResponseState.DEFERRING, ResponseState.DEFERRED ->
                    throw IllegalStateException("The interaction has already been deferred")

                ResponseState.RESPONDING, ResponseState.RESPONDED ->
                    throw IllegalStateException("The interaction has already been answered")
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
        mutex.withLock {
            val previous = state
            when (previous) {
                ResponseState.IDLE, ResponseState.DEFERRED -> Unit

                ResponseState.DEFERRING ->
                    throw IllegalStateException("The interaction is being deferred and cannot be answered yet")

                ResponseState.RESPONDING, ResponseState.RESPONDED ->
                    throw IllegalStateException("The interaction has already been answered")
            }

            state = ResponseState.RESPONDING
            try {
                if (previous == ResponseState.DEFERRED) {
                    delegate.editOriginal(response.content)
                } else {
                    delegate.reply(response.content, response.ephemeral)
                }
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
