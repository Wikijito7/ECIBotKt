package es.wokis.jda.command

import es.wokis.message.MessageView
import kotlinx.coroutines.delay

/**
 * Records every REST operation requested by [JdaCommandResponder] and injects one-shot failures.
 *
 * It also tracks the number of operations in flight so tests can assert the responder never
 * runs two REST actions concurrently.
 */
internal class FakeInteractionResponseDelegate : InteractionResponseDelegate {

    sealed interface Operation {
        data class Defer(val ephemeral: Boolean) : Operation
        data class Reply(val content: String, val ephemeral: Boolean) : Operation
        data class Edit(val content: String) : Operation
        data class ReplyView(val view: MessageView, val ephemeral: Boolean) : Operation
        data class EditView(val view: MessageView) : Operation
    }

    private val recordedOperations = mutableListOf<Operation>()
    private val deferFailures = ArrayDeque<Throwable>()
    private val replyFailures = ArrayDeque<Throwable>()
    private val editFailures = ArrayDeque<Throwable>()
    private val viewReplyFailures = ArrayDeque<Throwable>()
    private val viewEditFailures = ArrayDeque<Throwable>()
    private var activeOperations = 0

    var operationDelayMillis: Long = 0
    var maxConcurrentOperations: Int = 0
        private set

    val operations: List<Operation> get() = recordedOperations.toList()
    val defers: List<Operation.Defer> get() = recordedOperations.filterIsInstance<Operation.Defer>()
    val replies: List<Operation.Reply> get() = recordedOperations.filterIsInstance<Operation.Reply>()
    val edits: List<Operation.Edit> get() = recordedOperations.filterIsInstance<Operation.Edit>()
    val viewReplies: List<Operation.ReplyView> get() = recordedOperations.filterIsInstance<Operation.ReplyView>()
    val viewEdits: List<Operation.EditView> get() = recordedOperations.filterIsInstance<Operation.EditView>()

    fun failNextDefer(error: Throwable) {
        deferFailures.addLast(error)
    }

    fun failNextReply(error: Throwable) {
        replyFailures.addLast(error)
    }

    fun failNextEdit(error: Throwable) {
        editFailures.addLast(error)
    }

    fun failNextViewReply(error: Throwable) {
        viewReplyFailures.addLast(error)
    }

    fun failNextViewEdit(error: Throwable) {
        viewEditFailures.addLast(error)
    }

    override suspend fun defer(ephemeral: Boolean) {
        record(Operation.Defer(ephemeral), deferFailures)
    }

    override suspend fun reply(content: String, ephemeral: Boolean) {
        record(Operation.Reply(content, ephemeral), replyFailures)
    }

    override suspend fun editOriginal(content: String) {
        record(Operation.Edit(content), editFailures)
    }

    override suspend fun reply(view: MessageView, ephemeral: Boolean) {
        record(Operation.ReplyView(view, ephemeral), viewReplyFailures)
    }

    override suspend fun editOriginal(view: MessageView) {
        record(Operation.EditView(view), viewEditFailures)
    }

    private suspend fun record(operation: Operation, failures: ArrayDeque<Throwable>) {
        activeOperations++
        maxConcurrentOperations = maxOf(maxConcurrentOperations, activeOperations)
        recordedOperations += operation
        try {
            if (operationDelayMillis > 0) {
                delay(operationDelayMillis)
            }
            if (failures.isNotEmpty()) {
                throw failures.removeFirst()
            }
        } finally {
            activeOperations--
        }
    }
}
