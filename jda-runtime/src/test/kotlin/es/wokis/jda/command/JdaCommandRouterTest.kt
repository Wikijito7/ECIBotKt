package es.wokis.jda.command

import es.wokis.commands.neutral.CommandContext
import es.wokis.commands.neutral.CommandHandler
import es.wokis.commands.neutral.CommandResponder
import es.wokis.commands.neutral.CommandResponse
import es.wokis.domain.locale.LocaleCode
import es.wokis.domain.model.ChannelId
import es.wokis.domain.model.GuildId
import es.wokis.domain.model.UserId
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class JdaCommandRouterTest {

    private val mapper: JdaCommandContextMapper = mockk()
    private val event: SlashCommandInteractionEvent = mockk {
        every { name } returns COMMAND_NAME
    }
    private val context = CommandContext(
        commandName = COMMAND_NAME,
        guildId = GuildId(1uL),
        channelId = ChannelId(2uL),
        userId = UserId(3uL),
        locale = LocaleCode("en-US"),
    )

    @Test
    fun `Given a registered handler When the event arrives Then route the mapped context and its responder`() {
        // Given
        val responder = RecordingResponder()
        var receivedContext: CommandContext? = null
        var receivedResponder: CommandResponder? = null
        val router = router(
            handler { commandContext, commandResponder ->
                receivedContext = commandContext
                receivedResponder = commandResponder
                commandResponder.respond(CommandResponse("ok", ephemeral = true))
            },
            responder,
        )
        every { mapper.map(event) } returns context

        // When
        router.onSlashCommandInteraction(event)
        router.close()

        // Then
        assertSame(context, receivedContext)
        assertSame(responder, receivedResponder)
        assertEquals(listOf(CommandResponse("ok", ephemeral = true)), responder.responses)
    }

    @Test
    fun `Given a throwing handler When the event arrives Then send one generic ephemeral failure response`() {
        // Given
        val responder = RecordingResponder()
        val router = router(
            handler { _, _ -> throw IllegalStateException("boom") },
            responder,
        )
        every { mapper.map(event) } returns context

        // When
        router.onSlashCommandInteraction(event)
        router.close()

        // Then
        assertEquals(
            listOf(CommandResponse("The command failed. Please try again later.", ephemeral = true)),
            responder.responses,
        )
        assertTrue(responder.defers.isEmpty())
    }

    @Test
    fun `Given no matching handler When the event arrives Then reply once with the generic unsupported response`() {
        // Given
        val responder = RecordingResponder()
        var responderCreated = 0
        var factoryEvent: SlashCommandInteractionEvent? = null
        val router = JdaCommandRouter(
            handlers = listOf(handler(name = "another-command") { _, _ -> }),
            contextMapper = mapper,
            responderFactory = { receivedEvent ->
                responderCreated++
                factoryEvent = receivedEvent
                responder
            },
            scope = CoroutineScope(Dispatchers.Unconfined),
        )

        // When
        router.onSlashCommandInteraction(event)
        router.close()

        // Then
        assertEquals(1, responderCreated)
        assertSame(event, factoryEvent)
        assertEquals(
            listOf(CommandResponse("This command is not supported.", ephemeral = true)),
            responder.responses,
        )
        assertTrue(responder.defers.isEmpty())
        verify(exactly = 0) { mapper.map(any()) }
    }

    @Test
    fun `Given duplicate handler names When constructing the router Then reject the configuration`() {
        // Then
        assertThrows(IllegalArgumentException::class.java) {
            JdaCommandRouter(
                handlers = listOf(
                    handler(name = "duplicate") { _, _ -> },
                    handler(name = "duplicate") { _, _ -> },
                ),
                contextMapper = mapper,
                responderFactory = { RecordingResponder() },
                scope = CoroutineScope(Dispatchers.Unconfined),
            )
        }
    }

    @Test
    fun `Given a handler that already answered When it fails Then keep the router alive`() {
        // Given
        val responder = StrictResponder()
        val router = router(
            handler { _, commandResponder ->
                commandResponder.respond(CommandResponse("partial"))
                throw IllegalStateException("boom after response")
            },
            responder,
        )
        every { mapper.map(event) } returns context

        // When
        router.onSlashCommandInteraction(event)
        router.close()

        // Then
        assertEquals(listOf(CommandResponse("partial")), responder.responses)
    }

    @Test
    fun `Given a handler whose defer fails When it falls back Then restore IDLE and reply immediately`() {
        // Given
        val delegate = FakeInteractionResponseDelegate().apply {
            failNextDefer(IllegalStateException("defer rejected"))
        }
        val router = routerWithDelegate(delegate) { _, commandResponder ->
            runCatching { commandResponder.defer(ephemeral = true) }
            commandResponder.respond(CommandResponse("fallback", ephemeral = true))
        }
        every { mapper.map(event) } returns context

        // When
        router.onSlashCommandInteraction(event)
        router.close()

        // Then
        assertEquals(listOf(FakeInteractionResponseDelegate.Operation.Defer(true)), delegate.defers)
        assertEquals(listOf(FakeInteractionResponseDelegate.Operation.Reply("fallback", true)), delegate.replies)
        assertTrue(delegate.edits.isEmpty())
        assertEquals(1, delegate.maxConcurrentOperations)
    }

    @Test
    fun `Given an uncaught defer failure When the event arrives Then the router sends the generic reply`() {
        // Given
        val delegate = FakeInteractionResponseDelegate().apply {
            failNextDefer(IllegalStateException("defer rejected"))
        }
        val router = routerWithDelegate(delegate) { _, commandResponder ->
            commandResponder.defer(ephemeral = false)
        }
        every { mapper.map(event) } returns context

        // When
        router.onSlashCommandInteraction(event)
        router.close()

        // Then
        assertEquals(listOf(FakeInteractionResponseDelegate.Operation.Defer(false)), delegate.defers)
        assertEquals(listOf(FakeInteractionResponseDelegate.Operation.Reply(GENERIC_FAILURE_MESSAGE, true)), delegate.replies)
        assertTrue(delegate.edits.isEmpty())
        assertEquals(1, delegate.maxConcurrentOperations)
    }

    @Test
    fun `Given an uncaught immediate reply failure When the event arrives Then the router sends the generic reply`() {
        // Given
        val delegate = FakeInteractionResponseDelegate().apply {
            failNextReply(IllegalStateException("reply rejected"))
        }
        val router = routerWithDelegate(delegate) { _, commandResponder ->
            commandResponder.respond(CommandResponse("initial", ephemeral = false))
        }
        every { mapper.map(event) } returns context

        // When
        router.onSlashCommandInteraction(event)
        router.close()

        // Then
        assertTrue(delegate.defers.isEmpty())
        assertEquals(
            listOf(
                FakeInteractionResponseDelegate.Operation.Reply("initial", false),
                FakeInteractionResponseDelegate.Operation.Reply(GENERIC_FAILURE_MESSAGE, true),
            ),
            delegate.replies,
        )
        assertTrue(delegate.edits.isEmpty())
        assertEquals(1, delegate.maxConcurrentOperations)
    }

    private fun routerWithDelegate(
        delegate: FakeInteractionResponseDelegate,
        block: suspend (CommandContext, CommandResponder) -> Unit,
    ): JdaCommandRouter = JdaCommandRouter(
        handlers = listOf(handler(block = block)),
        contextMapper = mapper,
        responderFactory = { JdaCommandResponder(delegate) },
        scope = CoroutineScope(Dispatchers.Unconfined),
    )

    private fun router(handler: CommandHandler, responder: CommandResponder): JdaCommandRouter = JdaCommandRouter(
        handlers = listOf(handler),
        contextMapper = mapper,
        responderFactory = { responder },
        scope = CoroutineScope(Dispatchers.Unconfined),
    )

    private fun handler(
        name: String = COMMAND_NAME,
        block: suspend (CommandContext, CommandResponder) -> Unit,
    ): CommandHandler = object : CommandHandler {
        override val name: String = name

        override suspend fun handle(context: CommandContext, responder: CommandResponder) {
            block(context, responder)
        }
    }

    private class RecordingResponder : CommandResponder {
        val defers = mutableListOf<Boolean>()
        val responses = mutableListOf<CommandResponse>()

        override suspend fun defer(ephemeral: Boolean) {
            defers += ephemeral
        }

        override suspend fun respond(response: CommandResponse) {
            responses += response
        }
    }

    private class StrictResponder : CommandResponder {
        val responses = mutableListOf<CommandResponse>()

        override suspend fun defer(ephemeral: Boolean) = Unit

        override suspend fun respond(response: CommandResponse) {
            check(responses.isEmpty()) { "The interaction has already been answered" }
            responses += response
        }
    }

    private companion object {
        private const val COMMAND_NAME = "jda-walkthrough"
        private const val GENERIC_FAILURE_MESSAGE = "The command failed. Please try again later."
    }
}
