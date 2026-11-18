package es.wokis.jda.command

import es.wokis.commands.neutral.CommandResponder
import es.wokis.commands.neutral.CommandResponse
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent

/**
 * JDA implementation of the neutral responder contract.
 *
 * A mutex serializes REST actions through an explicit state machine: a state is committed only when
 * the delegate succeeds and the previous state is restored on failure, so a failed call never leaves
 * a false terminal state. After a successful defer the response edits the original message, and its
 * ephemeral flag is ignored because the defer fixed visibility.
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
