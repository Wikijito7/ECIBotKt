package es.wokis.jda.command

import es.wokis.commands.neutral.AutocompleteContext
import es.wokis.commands.neutral.AutocompleteHandler
import es.wokis.commands.neutral.AutocompleteResponder
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent
import net.dv8tion.jda.api.interactions.AutoCompleteQuery
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class JdaAutocompleteRouterTest {

    private val mapper: JdaAutocompleteContextMapper = mockk()
    private val responder: AutocompleteResponder = mockk()
    private val context = AutocompleteContext(
        commandName = "locale",
        optionName = "locale",
        guildId = null,
        input = "es",
    )

    @Test
    fun `Given a locale autocomplete When it arrives Then route the mapped context once`() {
        val locale = RecordingHandler("locale", "locale")
        val sound = RecordingHandler("sound", "name")
        val created = mutableListOf<CommandAutoCompleteInteractionEvent>()
        val router = router(listOf(locale, sound), created)
        val event = event("locale", "locale")

        try {
            router.onCommandAutoCompleteInteraction(event)

            assertEquals(1, locale.calls)
            assertEquals(0, sound.calls)
            assertEquals(listOf(event), created)
            verify(exactly = 1) { mapper.map(event) }
        } finally {
            router.close()
        }
    }

    @Test
    fun `Given a sound autocomplete When it arrives Then route the sound name handler`() {
        val locale = RecordingHandler("locale", "locale")
        val sound = RecordingHandler("sound", "name")
        val router = router(listOf(locale, sound))

        try {
            router.onCommandAutoCompleteInteraction(event("sound", "name"))

            assertEquals(0, locale.calls)
            assertEquals(1, sound.calls)
        } finally {
            router.close()
        }
    }

    @Test
    fun `Given an unknown command When autocomplete arrives Then ignore it without a responder or ack`() {
        val locale = RecordingHandler("locale", "locale")
        val created = mutableListOf<CommandAutoCompleteInteractionEvent>()
        val router = router(listOf(locale), created)

        try {
            router.onCommandAutoCompleteInteraction(event("unknown-command", "locale"))

            assertEquals(0, locale.calls)
            assertTrue(created.isEmpty())
            verify(exactly = 0) { mapper.map(any()) }
        } finally {
            router.close()
        }
    }

    @Test
    fun `Given a known command with an unknown option When autocomplete arrives Then ignore it without a responder`() {
        val locale = RecordingHandler("locale", "locale")
        val created = mutableListOf<CommandAutoCompleteInteractionEvent>()
        val router = router(listOf(locale), created)

        try {
            router.onCommandAutoCompleteInteraction(event("locale", "unregistered-option"))

            assertEquals(0, locale.calls)
            assertTrue(created.isEmpty())
            verify(exactly = 0) { mapper.map(any()) }
        } finally {
            router.close()
        }
    }

    @Test
    fun `Given a throwing handler When autocomplete arrives Then contain the failure and keep routing`() {
        val failing = RecordingHandler("locale", "locale") { throw IllegalStateException("boom") }
        val surviving = RecordingHandler("sound", "name")
        val router = router(listOf(failing, surviving))

        try {
            router.onCommandAutoCompleteInteraction(event("locale", "locale"))
            router.onCommandAutoCompleteInteraction(event("sound", "name"))

            assertEquals(1, failing.calls)
            assertEquals(1, surviving.calls)
        } finally {
            router.close()
        }
    }

    @Test
    fun `Given a handler that throws CancellationException When autocomplete arrives Then propagate the cancellation`() {
        val cancellation = CancellationException("autocomplete cancelled")
        val observedJob = AtomicReference<Job?>(null)
        val observedCause = AtomicReference<Throwable?>(null)
        val cancelled = RecordingHandler("locale", "locale") {
            val job = currentCoroutineContext()[Job]
            observedJob.set(job)
            job?.invokeOnCompletion { cause -> observedCause.set(cause) }
            throw cancellation
        }
        val surviving = RecordingHandler("sound", "name")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val router = router(listOf(cancelled, surviving), scope = scope)

        try {
            router.onCommandAutoCompleteInteraction(event("locale", "locale"))
            router.onCommandAutoCompleteInteraction(event("sound", "name"))

            assertEquals(1, cancelled.calls)
            val job = requireNotNull(observedJob.get()) { "The handler must run inside the router scope" }
            assertTrue(job.isCompleted)
            assertTrue(job.isCancelled, "The rethrown cancellation must cancel the coroutine, not complete it normally")
            assertSame(cancellation, observedCause.get())
            assertEquals(1, surviving.calls)
            assertTrue(scope.isActive)
        } finally {
            router.close()
        }
    }

    @Test
    fun `Given the router scope When autocomplete arrives Then handle it off the JDA thread`() {
        val executor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "autocomplete-router-worker")
        }
        val handled = CompletableDeferred<Unit>()
        val handlerThread = AtomicReference<String?>(null)
        val handler = RecordingHandler("locale", "locale") {
            handlerThread.set(Thread.currentThread().name)
            handled.complete(Unit)
        }
        val router = router(
            handlers = listOf(handler),
            scope = CoroutineScope(SupervisorJob() + executor.asCoroutineDispatcher()),
        )

        try {
            router.onCommandAutoCompleteInteraction(event("locale", "locale"))
            runBlocking {
                withTimeout(5_000) { handled.await() }
            }

            assertEquals(1, handler.calls)
            assertTrue(handlerThread.get()?.startsWith("autocomplete-router-worker") == true)
            assertNotEquals("autocomplete-router-worker", Thread.currentThread().name)
        } finally {
            router.close()
            executor.shutdownNow()
            executor.awaitTermination(5, TimeUnit.SECONDS)
        }
    }

    @Test
    fun `Given duplicate command and option pairs When constructing the router Then reject the configuration`() {
        assertThrows(IllegalArgumentException::class.java) {
            JdaAutocompleteRouter(
                handlers = listOf(
                    RecordingHandler("sound", "name"),
                    RecordingHandler("sound", "name"),
                ),
                contextMapper = mapper,
                responderFactory = { responder },
                scope = CoroutineScope(Dispatchers.Unconfined),
            )
        }
    }

    @Test
    fun `Given two options of the same command When constructed Then route each option to its own handler`() {
        val nameHandler = RecordingHandler("sound", "name")
        val otherHandler = RecordingHandler("sound", "other")
        val router = router(listOf(nameHandler, otherHandler))

        try {
            router.onCommandAutoCompleteInteraction(event("sound", "other"))

            assertEquals(0, nameHandler.calls)
            assertEquals(1, otherHandler.calls)
        } finally {
            router.close()
        }
    }

    @Test
    fun `Given a router When it is closed Then cancel its scope`() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val router = router(listOf(RecordingHandler("locale", "locale")), scope = scope)

        router.close()

        assertFalse(scope.isActive)
    }

    private fun router(
        handlers: List<AutocompleteHandler>,
        created: MutableList<CommandAutoCompleteInteractionEvent> = mutableListOf(),
        scope: CoroutineScope = CoroutineScope(Dispatchers.Unconfined),
    ): JdaAutocompleteRouter {
        every { mapper.map(any()) } returns context
        return JdaAutocompleteRouter(
            handlers = handlers,
            contextMapper = mapper,
            responderFactory = { event ->
                created += event
                responder
            },
            scope = scope,
        )
    }

    private fun event(commandName: String, optionName: String): CommandAutoCompleteInteractionEvent {
        val query = mockk<AutoCompleteQuery> {
            every { name } returns optionName
            every { value } returns "es"
        }
        return mockk {
            every { name } returns commandName
            every { focusedOption } returns query
        }
    }

    private class RecordingHandler(
        override val commandName: String,
        override val optionName: String,
        private val onHandle: suspend () -> Unit = {},
    ) : AutocompleteHandler {
        var calls = 0

        override suspend fun handle(context: AutocompleteContext, responder: AutocompleteResponder) {
            calls++
            onHandle()
        }
    }
}
