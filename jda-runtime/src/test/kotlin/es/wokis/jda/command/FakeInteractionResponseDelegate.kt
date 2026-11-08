package es.wokis.jda.command

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
    }

    private val recordedOperations = mutableListOf<Operation>()
    private val deferFailures = ArrayDeque<Throwable>()
    private val replyFailures = ArrayDeque<Throwable>()
    private val editFailures = ArrayDeque<Throwable>()
    private var activeOperations = 0

    var operationDelayMillis: Long = 0
    var maxConcurrentOperations: Int = 0
        private set

    val operations: List<Operation> get() = recordedOperations.toList()
    val defers: List<Operation.Defer> get() = recordedOperations.filterIsInstance<Operation.Defer>()
    val replies: List<Operation.Reply> get() = recordedOperations.filterIsInstance<Operation.Reply>()
    val edits: List<Operation.Edit> get() = recordedOperations.filterIsInstance<Operation.Edit>()

    fun failNextDefer(error: Throwable) {
        deferFailures.addLast(error)
    }

    fun failNextReply(error: Throwable) {
        replyFailures.addLast(error)
    }

    fun failNextEdit(error: Throwable) {
        editFailures.addLast(error)
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
