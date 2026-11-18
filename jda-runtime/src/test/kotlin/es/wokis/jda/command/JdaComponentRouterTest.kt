package es.wokis.jda.command

import es.wokis.commands.neutral.ComponentContext
import es.wokis.commands.neutral.ComponentHandler
import es.wokis.commands.neutral.ComponentResponder
import es.wokis.domain.model.ChannelId
import es.wokis.domain.model.UserId
import es.wokis.message.ComponentId
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class JdaComponentRouterTest {

    private val mapper: JdaComponentContextMapper = mockk()
    private val responder: ComponentResponder = mockk()
    private val context = ComponentContext(
        componentId = ComponentId.PLAYER_PAUSE,
        guildId = null,
        channelId = ChannelId(2uL),
        userId = UserId(3uL),
    )

    @Test
    fun `Given a bare component id When clicked Then route once to its handler`() {
        val handler = RecordingHandler(ComponentId.PLAYER_PAUSE)
        val event = event("player_pause")
        val router = router(listOf(handler))

        try {
            router.onButtonInteraction(event)

            assertEquals(1, handler.calls)
            verify(exactly = 1) { mapper.map(event, ComponentId.PLAYER_PAUSE) }
        } finally {
            router.close()
        }
    }

    @Test
    fun `Given a payload suffixed component id When clicked Then ignore the payload`() {
        val handler = RecordingHandler(ComponentId.PLAYER_PAUSE)
        val event = event("player_pause::state-42")
        val router = router(listOf(handler))

        try {
            router.onButtonInteraction(event)

            assertEquals(1, handler.calls)
            verify(exactly = 1) { mapper.map(event, ComponentId.PLAYER_PAUSE) }
        } finally {
            router.close()
        }
    }

    @Test
    fun `Given an unknown component id When clicked Then ignore it without acknowledging`() {
        val handler = RecordingHandler(ComponentId.PLAYER_PAUSE)
        val created = mutableListOf<ButtonInteractionEvent>()
        val router = router(listOf(handler), created)
        val event = event("playlist_unknown::payload")

        try {
            router.onButtonInteraction(event)

            assertEquals(0, handler.calls)
            assertEquals(0, created.size)
        } finally {
            router.close()
        }
    }

    @Test
    fun `Given a known component id without a handler When clicked Then ignore it without acknowledging`() {
        val handler = RecordingHandler(ComponentId.PLAYER_SKIP)
        val created = mutableListOf<ButtonInteractionEvent>()
        val router = router(listOf(handler), created)
        val event = event("player_pause")

        try {
            router.onButtonInteraction(event)

            assertEquals(0, handler.calls)
            assertEquals(0, created.size)
        } finally {
            router.close()
        }
    }

    @Test
    fun `Given a message without an embed When clicked Then ignore it before mapping without acknowledging`() {
        val handler = RecordingHandler(ComponentId.PLAYER_PAUSE)
        val created = mutableListOf<ButtonInteractionEvent>()
        val router = router(listOf(handler), created)
        val event = event("player_pause", hasEmbed = false)

        try {
            router.onButtonInteraction(event)

            assertEquals(0, handler.calls)
            assertEquals(0, created.size)
            // Then the embed guard runs before any context mapping or responder creation
            verify(exactly = 0) { mapper.map(any(), any()) }
        } finally {
            router.close()
        }
    }

    @Test
    fun `Given a message whose embeds cannot be read When clicked Then contain the failure and keep routing`() {
        val failing = RecordingHandler(ComponentId.PLAYER_PAUSE)
        val surviving = RecordingHandler(ComponentId.PLAYER_SKIP)
        val created = mutableListOf<ButtonInteractionEvent>()
        val router = router(listOf(failing, surviving), created)
        val brokenEvent = event("player_pause")
        every { brokenEvent.message.embeds } throws IllegalStateException("message unavailable")
        val survivingEvent = event("player_skip")

        try {
            // When the guard now runs inside the router coroutine, a broken message must not escape
            router.onButtonInteraction(brokenEvent)
            router.onButtonInteraction(survivingEvent)

            // Then the broken click is contained and the next click still routes
            assertEquals(0, failing.calls)
            assertEquals(1, surviving.calls)
            assertEquals(listOf(survivingEvent), created)
        } finally {
            router.close()
        }
    }

    @Test
    fun `Given a throwing handler When clicked Then contain the failure and keep routing`() {
        val failing = RecordingHandler(ComponentId.PLAYER_PAUSE) { throw IllegalStateException("boom") }
        val surviving = RecordingHandler(ComponentId.PLAYER_SKIP)
        val router = router(listOf(failing, surviving))
        val failingEvent = event("player_pause")
        val survivingEvent = event("player_skip")

        try {
            router.onButtonInteraction(failingEvent)
            router.onButtonInteraction(survivingEvent)

            assertEquals(1, failing.calls)
            assertEquals(1, surviving.calls)
        } finally {
            router.close()
        }
    }

    @Test
    fun `Given the router scope When clicked Then handle the button off the JDA thread`() {
        val executor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "component-router-worker")
        }
        val handled = CompletableDeferred<Unit>()
        val handlerThread = AtomicReference<String?>(null)
        val handler = RecordingHandler(ComponentId.PLAYER_PAUSE) {
            handlerThread.set(Thread.currentThread().name)
            handled.complete(Unit)
        }
        val router = router(
            handlers = listOf(handler),
            scope = CoroutineScope(SupervisorJob() + executor.asCoroutineDispatcher()),
        )
        val event = event("player_pause")

        try {
            router.onButtonInteraction(event)
            runBlocking {
                withTimeout(5_000) { handled.await() }
            }

            assertEquals(1, handler.calls)
            assertTrue(handlerThread.get()?.startsWith("component-router-worker") == true)
            assertNotEquals("component-router-worker", Thread.currentThread().name)
        } finally {
            router.close()
            executor.shutdownNow()
            executor.awaitTermination(5, TimeUnit.SECONDS)
        }
    }

    @Test
    fun `Given duplicate handler ids When constructing the router Then reject the configuration`() {
        assertThrows(IllegalArgumentException::class.java) {
            JdaComponentRouter(
                handlers = listOf(
                    RecordingHandler(ComponentId.PLAYER_PAUSE),
                    RecordingHandler(ComponentId.PLAYER_PAUSE),
                ),
                contextMapper = mapper,
                responderFactory = { responder },
                scope = CoroutineScope(Dispatchers.Unconfined),
            )
        }
    }

    private fun router(
        handlers: List<ComponentHandler>,
        created: MutableList<ButtonInteractionEvent> = mutableListOf(),
        scope: CoroutineScope = CoroutineScope(Dispatchers.Unconfined),
    ): JdaComponentRouter {
        every { mapper.map(any(), any()) } returns context
        return JdaComponentRouter(
            handlers = handlers,
            contextMapper = mapper,
            responderFactory = { event ->
                created += event
                responder
            },
            scope = scope,
        )
    }

    private fun event(customId: String, hasEmbed: Boolean = true): ButtonInteractionEvent {
        val embedsValue = if (hasEmbed) listOf(mockk<MessageEmbed>()) else emptyList()
        val message = mockk<Message>()
        every { message.embeds } returns embedsValue
        val event = mockk<ButtonInteractionEvent>()
        every { event.componentId } returns customId
        every { event.message } returns message
        return event
    }

    private class RecordingHandler(
        override val id: ComponentId,
        private val onHandle: suspend () -> Unit = {},
    ) : ComponentHandler {
        var calls = 0

        override suspend fun handle(context: ComponentContext, responder: ComponentResponder) {
            calls++
            onHandle()
        }
    }
}
