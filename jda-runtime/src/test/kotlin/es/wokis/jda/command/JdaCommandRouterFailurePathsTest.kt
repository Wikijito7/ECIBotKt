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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.DiscordLocale
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.coroutines.coroutineContext

/**
 * Failure and lifecycle paths of [JdaCommandRouter].
 *
 * Every scenario drives the router with an eagerly dispatched, injected scope so the assertions
 * run after the launched coroutine already finished. The default collaborators are exercised
 * separately with real wait points instead of assuming a dispatch order.
 */
class JdaCommandRouterFailurePathsTest {

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
    fun `Given a handler that is cancelled When the event arrives Then propagate it without replying`() {
        // Given
        val failures = mutableListOf<Throwable>()
        val responder = ThrowingResponder { IllegalStateException("the router must not reply") }
        var commandJob: Job? = null
        val router = JdaCommandRouter(
            handlers = listOf(
                handler { _, _ ->
                    commandJob = coroutineContext[Job]
                    throw CancellationException("command cancelled")
                },
            ),
            contextMapper = mapper,
            responderFactory = { responder },
            scope = collectingScope(failures),
        )
        every { mapper.map(event) } returns context

        // When
        router.onSlashCommandInteraction(event)
        router.close()

        // Then
        assertTrue(responder.responses.isEmpty(), "A cancelled handler must not receive a failure reply")
        assertTrue(requireNotNull(commandJob).isCancelled, "The cancellation must reach the launched coroutine")
        assertTrue(failures.isEmpty(), "Cancellation must not surface as an uncaught coroutine error")
    }

    @Test
    fun `Given a cancelled failure reply When the handler fails Then propagate it without a second attempt`() {
        // Given
        val responder = ThrowingResponder { CancellationException("the interaction was cancelled") }
        val router = JdaCommandRouter(
            handlers = listOf(handler { _, _ -> throw IllegalStateException("boom") }),
            contextMapper = mapper,
            responderFactory = { responder },
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        )
        every { mapper.map(event) } returns context

        // When
        router.onSlashCommandInteraction(event)
        router.close()

        // Then
        assertEquals(
            listOf(CommandResponse(GENERIC_FAILURE_MESSAGE, ephemeral = true)),
            responder.responses,
            "The generic fallback must be attempted exactly once",
        )
        assertTrue(requireNotNull(responder.commandJob).isCancelled)
    }

    @Test
    fun `Given a failing unsupported reply When handled Then log it and complete the coroutine`() {
        // Given
        val failures = mutableListOf<Throwable>()
        val responder = ThrowingResponder { IllegalStateException("interaction already acknowledged") }
        val router = JdaCommandRouter(
            handlers = emptyList(),
            contextMapper = mapper,
            responderFactory = { responder },
            scope = collectingScope(failures),
        )

        // When
        router.onSlashCommandInteraction(event)
        router.close()

        // Then
        assertEquals(
            listOf(CommandResponse(UNSUPPORTED_COMMAND_MESSAGE, ephemeral = true)),
            responder.responses,
        )
        val commandJob = requireNotNull(responder.commandJob)
        assertTrue(commandJob.isCompleted)
        assertFalse(commandJob.isCancelled, "A handled fallback error must not cancel the coroutine")
        assertTrue(failures.isEmpty(), "A handled fallback error must not surface as an uncaught coroutine error")
    }

    @Test
    fun `Given a cancelled unsupported reply When handled Then propagate the cancellation`() {
        // Given
        val failures = mutableListOf<Throwable>()
        val responder = ThrowingResponder { CancellationException("interaction cancelled") }
        val router = JdaCommandRouter(
            handlers = emptyList(),
            contextMapper = mapper,
            responderFactory = { responder },
            scope = collectingScope(failures),
        )

        // When
        router.onSlashCommandInteraction(event)
        router.close()

        // Then
        assertEquals(
            listOf(CommandResponse(UNSUPPORTED_COMMAND_MESSAGE, ephemeral = true)),
            responder.responses,
        )
        assertTrue(requireNotNull(responder.commandJob).isCancelled)
        assertTrue(failures.isEmpty())
    }

    @Test
    fun `Given repeated handler and fallback failures When events arrive Then keep the router alive`() {
        // Given
        val failures = mutableListOf<Throwable>()
        val responder = ThrowingResponder { IllegalStateException("interaction already acknowledged") }
        val router = JdaCommandRouter(
            handlers = listOf(handler { _, _ -> throw IllegalStateException("boom") }),
            contextMapper = mapper,
            responderFactory = { responder },
            scope = collectingScope(failures),
        )
        every { mapper.map(event) } returns context

        // When
        router.onSlashCommandInteraction(event)
        router.onSlashCommandInteraction(event)
        router.close()

        // Then
        assertEquals(2, responder.responses.size, "Every event must be routed even after a double failure")
        assertTrue(failures.isEmpty())
    }

    @Test
    fun `Given only handlers When the event arrives Then use the default mapper, responder and scope`() {
        // Given
        val realEvent = guildEvent()
        val handled = CompletableDeferred<CommandContext>()
        var receivedResponder: CommandResponder? = null
        val router = JdaCommandRouter(
            handlers = listOf(
                handler { commandContext, responder ->
                    receivedResponder = responder
                    handled.complete(commandContext)
                },
            ),
        )

        try {
            // When
            router.onSlashCommandInteraction(realEvent)
            val mappedContext = runBlocking { withTimeout(TEST_TIMEOUT_MILLIS) { handled.await() } }

            // Then
            assertEquals(COMMAND_NAME, mappedContext.commandName)
            assertEquals(GuildId(10uL), mappedContext.guildId)
            assertEquals(ChannelId(20uL), mappedContext.channelId)
            assertEquals(UserId(30uL), mappedContext.userId)
            assertEquals(LocaleCode("en-US"), mappedContext.locale)
            assertTrue(receivedResponder is JdaCommandResponder)
        } finally {
            router.close()
        }
    }

    @Test
    fun `Given the default scope When the router closes Then cancel the in-flight command`() {
        // Given
        val realEvent = guildEvent()
        val started = CompletableDeferred<Job>()
        val router = JdaCommandRouter(
            handlers = listOf(
                handler { _, _ ->
                    started.complete(requireNotNull(coroutineContext[Job]))
                    awaitCancellation()
                },
            ),
        )

        try {
            // When
            router.onSlashCommandInteraction(realEvent)
            val commandJob = runBlocking { withTimeout(TEST_TIMEOUT_MILLIS) { started.await() } }
            router.close()
            runBlocking { withTimeout(TEST_TIMEOUT_MILLIS) { commandJob.join() } }

            // Then
            assertTrue(commandJob.isCancelled)
            assertTrue(commandJob.isCompleted)
        } finally {
            router.close()
        }
    }

    private fun collectingScope(failures: MutableList<Throwable>): CoroutineScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Unconfined +
            CoroutineExceptionHandler { _, error -> failures += error },
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

    private fun guildEvent(): SlashCommandInteractionEvent {
        val guild = mockk<Guild> {
            every { idLong } returns 10L
            every { locale } returns DiscordLocale.ENGLISH_US
        }
        val channel = mockk<MessageChannelUnion> {
            every { idLong } returns 20L
        }
        val user = mockk<User> {
            every { idLong } returns 30L
        }
        return mockk<SlashCommandInteractionEvent>().also { slashEvent ->
            every { slashEvent.name } returns COMMAND_NAME
            every { slashEvent.guild } returns guild
            every { slashEvent.channel } returns channel
            every { slashEvent.user } returns user
            every { slashEvent.userLocale } returns DiscordLocale.ENGLISH_US
        }
    }

    private class ThrowingResponder(
        private val error: () -> Throwable,
    ) : CommandResponder {

        val responses = mutableListOf<CommandResponse>()
        var commandJob: Job? = null
            private set

        override suspend fun defer(ephemeral: Boolean) = Unit

        override suspend fun respond(response: CommandResponse) {
            responses += response
            commandJob = coroutineContext[Job]
            throw error()
        }
    }

    private companion object {
        private const val COMMAND_NAME = "jda-walkthrough"
        private const val GENERIC_FAILURE_MESSAGE = "The command failed. Please try again later."
        private const val UNSUPPORTED_COMMAND_MESSAGE = "This command is not supported."
        private const val TEST_TIMEOUT_MILLIS = 10_000L
    }
}
