package es.wokis.jda.command

import es.wokis.commands.neutral.CommandResponse
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class JdaCommandResponderTest {

    private lateinit var delegate: FakeInteractionResponseDelegate
    private lateinit var responder: JdaCommandResponder

    @BeforeEach
    fun setUp() {
        delegate = FakeInteractionResponseDelegate()
        responder = JdaCommandResponder(delegate)
    }

    @Test
    fun `Given no defer When responding Then send one immediate reply with requested visibility`() = runTest {
        responder.respond(CommandResponse("hello", ephemeral = true))

        assertTrue(delegate.defers.isEmpty())
        assertEquals(listOf(FakeInteractionResponseDelegate.Operation.Reply("hello", true)), delegate.replies)
        assertTrue(delegate.edits.isEmpty())
    }

    @Test
    fun `Given no defer and a public response When responding Then send one public reply`() = runTest {
        responder.respond(CommandResponse("hello", ephemeral = false))

        assertTrue(delegate.defers.isEmpty())
        assertEquals(listOf(FakeInteractionResponseDelegate.Operation.Reply("hello", false)), delegate.replies)
    }

    @Test
    fun `Given ephemeral defer When responding Then edit the deferred original and keep its visibility`() = runTest {
        responder.defer(ephemeral = true)
        responder.respond(CommandResponse("done", ephemeral = false))

        assertEquals(listOf(FakeInteractionResponseDelegate.Operation.Defer(true)), delegate.defers)
        assertTrue(delegate.replies.isEmpty())
        assertEquals(listOf(FakeInteractionResponseDelegate.Operation.Edit("done")), delegate.edits)
    }

    @Test
    fun `Given public defer When responding ephemeral Then edit the original instead of hiding it`() = runTest {
        responder.defer(ephemeral = false)
        responder.respond(CommandResponse("done", ephemeral = true))

        assertEquals(listOf(FakeInteractionResponseDelegate.Operation.Defer(false)), delegate.defers)
        assertTrue(delegate.replies.isEmpty())
        assertEquals(listOf(FakeInteractionResponseDelegate.Operation.Edit("done")), delegate.edits)
    }

    @Test
    fun `Given already deferred interaction When deferring again Then reject without a second REST call`() = runTest {
        responder.defer(ephemeral = false)

        val error = expectIllegalState { responder.defer(ephemeral = true) }

        assertTrue(error.message!!.contains("already been deferred"))
        assertEquals(listOf(FakeInteractionResponseDelegate.Operation.Defer(false)), delegate.defers)
    }

    @Test
    fun `Given already responded interaction When deferring Then reject without a REST call`() = runTest {
        responder.respond(CommandResponse("hello"))

        val error = expectIllegalState { responder.defer(ephemeral = true) }

        assertTrue(error.message!!.contains("already been answered"))
        assertTrue(delegate.defers.isEmpty())
    }

    @Test
    fun `Given already responded interaction When responding again Then reject without a second response`() = runTest {
        responder.respond(CommandResponse("first"))

        val error = expectIllegalState { responder.respond(CommandResponse("second")) }

        assertTrue(error.message!!.contains("already been answered"))
        assertEquals(listOf(FakeInteractionResponseDelegate.Operation.Reply("first", false)), delegate.replies)
    }

    @Test
    fun `Given deferred interaction When responding twice Then reject the second edit`() = runTest {
        responder.defer(ephemeral = true)
        responder.respond(CommandResponse("first"))

        val error = expectIllegalState { responder.respond(CommandResponse("second")) }

        assertTrue(error.message!!.contains("already been answered"))
        assertEquals(listOf(FakeInteractionResponseDelegate.Operation.Edit("first")), delegate.edits)
    }

    @Test
    fun `Given a failing defer When the failure is handled Then restore IDLE and allow an immediate reply`() = runTest {
        delegate.failNextDefer(IllegalStateException("defer rejected"))

        val error = runCatching { responder.defer(ephemeral = true) }.exceptionOrNull()

        assertTrue(error is IllegalStateException, "Expected IllegalStateException but was $error")
        assertEquals(listOf(FakeInteractionResponseDelegate.Operation.Defer(true)), delegate.defers)

        responder.respond(CommandResponse("fallback", ephemeral = true))

        assertEquals(listOf(FakeInteractionResponseDelegate.Operation.Reply("fallback", true)), delegate.replies)
        assertTrue(delegate.edits.isEmpty())
    }

    @Test
    fun `Given a failing defer When deferring again Then retry the defer and edit the original`() = runTest {
        delegate.failNextDefer(IllegalStateException("defer rejected"))
        runCatching { responder.defer(ephemeral = true) }

        responder.defer(ephemeral = false)
        responder.respond(CommandResponse("done"))

        assertEquals(
            listOf(
                FakeInteractionResponseDelegate.Operation.Defer(true),
                FakeInteractionResponseDelegate.Operation.Defer(false),
            ),
            delegate.defers,
        )
        assertTrue(delegate.replies.isEmpty())
        assertEquals(listOf(FakeInteractionResponseDelegate.Operation.Edit("done")), delegate.edits)
    }

    @Test
    fun `Given a failing immediate reply When retried Then the second reply succeeds`() = runTest {
        delegate.failNextReply(IllegalStateException("reply rejected"))

        val error = runCatching { responder.respond(CommandResponse("first")) }.exceptionOrNull()

        assertTrue(error is IllegalStateException, "Expected IllegalStateException but was $error")
        assertEquals(listOf(FakeInteractionResponseDelegate.Operation.Reply("first", false)), delegate.replies)

        responder.respond(CommandResponse("retry"))

        assertEquals(
            listOf(
                FakeInteractionResponseDelegate.Operation.Reply("first", false),
                FakeInteractionResponseDelegate.Operation.Reply("retry", false),
            ),
            delegate.replies,
        )
        assertTrue(delegate.edits.isEmpty())
    }

    @Test
    fun `Given a failing deferred edit When retried Then keep DEFERRED and edit the original again`() = runTest {
        responder.defer(ephemeral = true)
        delegate.failNextEdit(IllegalStateException("edit rejected"))

        val error = runCatching { responder.respond(CommandResponse("first")) }.exceptionOrNull()

        assertTrue(error is IllegalStateException, "Expected IllegalStateException but was $error")
        assertEquals(listOf(FakeInteractionResponseDelegate.Operation.Defer(true)), delegate.defers)
        assertEquals(listOf(FakeInteractionResponseDelegate.Operation.Edit("first")), delegate.edits)

        responder.respond(CommandResponse("second"))

        assertTrue(delegate.replies.isEmpty())
        assertEquals(
            listOf(
                FakeInteractionResponseDelegate.Operation.Edit("first"),
                FakeInteractionResponseDelegate.Operation.Edit("second"),
            ),
            delegate.edits,
        )
    }

    @Test
    fun `Given concurrent responses When both complete Then only one REST action runs at a time`() = runTest {
        delegate.operationDelayMillis = 100

        val results = listOf(
            async { runCatching { responder.respond(CommandResponse("first", ephemeral = true)) } },
            async { runCatching { responder.respond(CommandResponse("second", ephemeral = false)) } },
        ).awaitAll()

        assertEquals(1, results.count { it.isSuccess })
        assertEquals(1, results.count { it.exceptionOrNull() is IllegalStateException })
        assertEquals(1, delegate.replies.size)
        assertEquals(1, delegate.maxConcurrentOperations)
    }

    private suspend fun expectIllegalState(block: suspend () -> Unit): IllegalStateException {
        val error = runCatching { block() }.exceptionOrNull()
        assertTrue(error is IllegalStateException, "Expected IllegalStateException but was $error")
        return error as IllegalStateException
    }
}
