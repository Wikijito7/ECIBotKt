package es.wokis.jda.command

import es.wokis.message.EmbedView
import es.wokis.message.MessageView
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

class JdaComponentResponderTest {

    private val view = MessageView(embed = EmbedView(title = "Player"))

    @Test
    fun `Given a deferred interaction When editing Then edit the original exactly once`() = runTest {
        val delegate = RecordingComponentDelegate()
        val responder = JdaComponentResponder(delegate)

        responder.deferEdit()
        responder.edit(view)

        assertEquals(listOf("defer", "edit-original"), delegate.operations)
    }

    @Test
    fun `Given a fresh interaction When editing without defer Then edit the clicked message`() = runTest {
        val delegate = RecordingComponentDelegate()
        val responder = JdaComponentResponder(delegate)

        responder.edit(view)

        assertEquals(listOf("edit-message"), delegate.operations)
    }

    @Test
    fun `Given a failed defer When editing Then fall back to a direct message edit`() = runTest {
        val delegate = RecordingComponentDelegate().also {
            it.failNextDefer(IllegalStateException("defer rejected"))
        }
        val responder = JdaComponentResponder(delegate)

        assertFailsWith<IllegalStateException> { responder.deferEdit() }
        responder.edit(view)

        assertEquals(listOf("defer", "edit-message"), delegate.operations)
    }

    @Test
    fun `Given a failed defer When deferring again Then retry and edit the original after success`() = runTest {
        val delegate = RecordingComponentDelegate().also {
            it.failNextDefer(IllegalStateException("defer rejected"))
        }
        val responder = JdaComponentResponder(delegate)

        assertFailsWith<IllegalStateException> { responder.deferEdit() }
        responder.deferEdit()
        responder.edit(view)

        assertEquals(listOf("defer", "defer", "edit-original"), delegate.operations)
    }

    @Test
    fun `Given an already deferred interaction When deferring again Then reject without a second REST call`() = runTest {
        val delegate = RecordingComponentDelegate()
        val responder = JdaComponentResponder(delegate)
        responder.deferEdit()

        val error = assertFailsWith<IllegalStateException> { responder.deferEdit() }

        assertEquals("The interaction has already been deferred", error.message)
        assertEquals(listOf("defer"), delegate.operations)
    }

    @Test
    fun `Given an already edited interaction When editing again Then reject without a second edit`() = runTest {
        val delegate = RecordingComponentDelegate()
        val responder = JdaComponentResponder(delegate)
        responder.edit(view)

        val error = assertFailsWith<IllegalStateException> { responder.edit(view) }

        assertEquals("The interaction has already been edited", error.message)
        assertEquals(listOf("edit-message"), delegate.operations)
    }

    @Test
    fun `Given an edited interaction When deferring Then reject the obsolete defer`() = runTest {
        val delegate = RecordingComponentDelegate()
        val responder = JdaComponentResponder(delegate)
        responder.edit(view)

        val error = assertFailsWith<IllegalStateException> { responder.deferEdit() }

        assertEquals("The interaction has already been edited", error.message)
        assertEquals(listOf("edit-message"), delegate.operations)
    }

    @Test
    fun `Given two concurrent edits When both run Then exactly one edit reaches the gateway`() = runTest {
        val delegate = RecordingComponentDelegate().also { it.operationDelayMillis = 10 }
        val responder = JdaComponentResponder(delegate)

        val outcomes = coroutineScope {
            listOf(
                async { runCatching { responder.edit(view) } },
                async { runCatching { responder.edit(view) } },
            ).awaitAll()
        }

        assertEquals(1, outcomes.count { it.isSuccess })
        assertEquals(1, delegate.operations.size)
        assertEquals(listOf("edit-message"), delegate.operations)
    }

    /**
     * Records the gateway operations the responder requests and can fail the next defer or delay an
     * operation so concurrent calls overlap.
     */
    private class RecordingComponentDelegate : ComponentResponseDelegate {
        val operations = mutableListOf<String>()
        var operationDelayMillis: Long = 0
        private var deferFailure: Throwable? = null

        fun failNextDefer(error: Throwable) {
            deferFailure = error
        }

        override suspend fun deferEdit() {
            record("defer")
            deferFailure?.let { failure ->
                deferFailure = null
                throw failure
            }
        }

        override suspend fun editMessage(view: MessageView) {
            record("edit-message")
        }

        override suspend fun editOriginal(view: MessageView) {
            record("edit-original")
        }

        private suspend fun record(operation: String) {
            operations += operation
            if (operationDelayMillis > 0) {
                delay(operationDelayMillis)
            }
        }
    }
}
