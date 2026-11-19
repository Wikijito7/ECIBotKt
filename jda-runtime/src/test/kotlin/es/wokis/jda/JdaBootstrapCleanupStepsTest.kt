package es.wokis.jda

import es.wokis.jda.player.JdaPlayerRuntime
import es.wokis.player.engine.PlayerEngine
import es.wokis.player.engine.PlayerEngineFactory
import es.wokis.player.engine.PlayerEngines
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.flow.emptyFlow
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.managers.AudioManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class JdaBootstrapCleanupStepsTest {

    @Test
    fun `Given every target When production cleanup runs Then voice disconnects before router hook and JDA shutdown`() {
        val events = mutableListOf<String>()
        val audioManager = mockk<AudioManager>()
        val guild = mockk<Guild>()
        val client = mockk<JDA>()
        val runtime = mockk<Runtime>()
        val hook = Thread({ }, "test-shutdown-hook")
        val router = AutoCloseable { events += "command-router" }
        val componentRouter = AutoCloseable { events += "component-router" }
        val autocompleteRouter = AutoCloseable { events += "autocomplete-router" }

        every { audioManager.isConnected } returns true
        every { audioManager.closeAudioConnection() } answers { events += "voice-disconnect" }
        every { guild.audioManager } returns audioManager
        every { client.status } returns JDA.Status.CONNECTED
        every { client.guilds } returns listOf(guild)
        every { client.shutdown() } answers { events += "jda-shutdown" }
        every { client.awaitShutdown(10L, TimeUnit.SECONDS) } returns true
        every { runtime.removeShutdownHook(hook) } answers {
            events += "shutdown-hook"
            true
        }

        mockkStatic(Runtime::class)
        try {
            every { Runtime.getRuntime() } returns runtime

            val steps = defaultBootstrapCleanupSteps(
                BootstrapCleanupTargets(
                    jda = client,
                    router = router,
                    shutdownHook = hook,
                    componentRouter = componentRouter,
                    autocompleteRouter = autocompleteRouter,
                ),
            )
            steps.forEach { it.run() }
        } finally {
            unmockkStatic(Runtime::class)
        }

        assertEquals(
            listOf(
                "voice-disconnect",
                "command-router",
                "component-router",
                "autocomplete-router",
                "shutdown-hook",
                "jda-shutdown",
            ),
            events,
        )
    }

    @Test
    fun `Given only a component router When production cleanup steps are built Then it closes alone`() {
        val componentRouter = AutoCloseable { }

        val steps = defaultBootstrapCleanupSteps(
            BootstrapCleanupTargets(jda = null, router = null, shutdownHook = null, componentRouter = componentRouter),
        )

        assertEquals(listOf("component-router"), steps.map { it.name })
    }

    @Test
    fun `Given only an autocomplete router When production cleanup steps are built Then it closes alone`() {
        val autocompleteRouter = AutoCloseable { }

        val steps = defaultBootstrapCleanupSteps(
            BootstrapCleanupTargets(
                jda = null,
                router = null,
                shutdownHook = null,
                autocompleteRouter = autocompleteRouter,
            ),
        )

        assertEquals(listOf("autocomplete-router"), steps.map { it.name })
    }

    @Test
    fun `Given a component router failure When cleanup runs Then the autocomplete router still closes before the hook`() {
        val events = mutableListOf<String>()
        val client = mockk<JDA>()
        val runtime = mockk<Runtime>()
        val hook = Thread({ }, "test-shutdown-hook")
        val componentRouter = AutoCloseable { throw IllegalStateException("component router failed") }
        val autocompleteRouter = AutoCloseable { events += "autocomplete-router" }

        every { client.status } returns JDA.Status.CONNECTED
        every { client.guilds } returns emptyList()
        every { client.shutdown() } answers { events += "jda-shutdown" }
        every { client.awaitShutdown(10L, TimeUnit.SECONDS) } returns true
        every { runtime.removeShutdownHook(hook) } answers {
            events += "shutdown-hook"
            true
        }

        mockkStatic(Runtime::class)
        try {
            every { Runtime.getRuntime() } returns runtime

            val report = runBootstrapCleanup(
                defaultBootstrapCleanupSteps(
                    BootstrapCleanupTargets(
                        jda = client,
                        router = null,
                        shutdownHook = hook,
                        componentRouter = componentRouter,
                        autocompleteRouter = autocompleteRouter,
                    ),
                ),
            )

            assertTrue(report.failed)
            assertEquals(listOf("autocomplete-router", "shutdown-hook", "jda-shutdown"), events)
        } finally {
            unmockkStatic(Runtime::class)
        }
    }

    @Test
    fun `Given a command router failure When production cleanup runs Then the component router still closes before the hook`() {
        val events = mutableListOf<String>()
        val client = mockk<JDA>()
        val runtime = mockk<Runtime>()
        val hook = Thread({ }, "test-shutdown-hook")
        val router = AutoCloseable { throw IllegalStateException("command router failed") }
        val componentRouter = AutoCloseable { events += "component-router" }

        every { client.status } returns JDA.Status.CONNECTED
        every { client.guilds } returns emptyList()
        every { client.shutdown() } answers { events += "jda-shutdown" }
        every { client.awaitShutdown(10L, TimeUnit.SECONDS) } returns true
        every { runtime.removeShutdownHook(hook) } answers {
            events += "shutdown-hook"
            true
        }

        mockkStatic(Runtime::class)
        try {
            every { Runtime.getRuntime() } returns runtime

            val report = runBootstrapCleanup(
                defaultBootstrapCleanupSteps(
                    BootstrapCleanupTargets(
                        jda = client,
                        router = router,
                        shutdownHook = hook,
                        componentRouter = componentRouter,
                    ),
                ),
            )

            assertTrue(report.failed)
            assertEquals(listOf("component-router", "shutdown-hook", "jda-shutdown"), events)
        } finally {
            unmockkStatic(Runtime::class)
        }
    }

    @Test
    fun `Given a voice disconnect failure When production cleanup runs Then router hook and JDA shutdown still run`() {
        val events = mutableListOf<String>()
        val audioManager = mockk<AudioManager>()
        val guild = mockk<Guild>()
        val client = mockk<JDA>()
        val runtime = mockk<Runtime>()
        val hook = Thread({ }, "test-shutdown-hook")
        val router = AutoCloseable { events += "command-router" }

        every { audioManager.isConnected } returns true
        every { audioManager.closeAudioConnection() } throws IllegalStateException("voice failed")
        every { guild.audioManager } returns audioManager
        every { client.status } returns JDA.Status.CONNECTED
        every { client.guilds } returns listOf(guild)
        every { client.shutdown() } answers { events += "jda-shutdown" }
        every { client.awaitShutdown(10L, TimeUnit.SECONDS) } returns true
        every { runtime.removeShutdownHook(hook) } answers {
            events += "shutdown-hook"
            true
        }

        mockkStatic(Runtime::class)
        try {
            every { Runtime.getRuntime() } returns runtime

            val report = runBootstrapCleanup(
                defaultBootstrapCleanupSteps(
                    BootstrapCleanupTargets(jda = client, router = router, shutdownHook = hook),
                ),
            )

            assertTrue(report.failed)
            assertEquals(
                listOf("command-router", "shutdown-hook", "jda-shutdown"),
                events,
            )
        } finally {
            unmockkStatic(Runtime::class)
        }
    }

    @Test
    fun `Given absent targets When production cleanup steps are built Then only the available resources are cleaned`() {
        assertTrue(defaultBootstrapCleanupSteps(BootstrapCleanupTargets(null, null, null)).isEmpty())

        val router = AutoCloseable { }
        val routerOnly = defaultBootstrapCleanupSteps(
            BootstrapCleanupTargets(jda = null, router = router, shutdownHook = null),
        )
        assertEquals(listOf("command-router"), routerOnly.map { it.name })
    }

    @Test
    fun `Given a player runtime When production cleanup steps are built Then it is closed first`() {
        val events = mutableListOf<String>()
        val playerRuntime = AutoCloseable { events += "player-runtime" }

        val steps = defaultBootstrapCleanupSteps(
            BootstrapCleanupTargets(jda = null, router = null, shutdownHook = null, playerRuntime = playerRuntime),
        )
        steps.single().run()

        assertEquals(listOf("player-runtime"), steps.map { it.name })
        assertEquals(listOf("player-runtime"), events)
    }

    @Test
    fun `Given a production player runtime When cleanup runs Then its shutdown is awaited before later steps`() {
        val closeStarted = CountDownLatch(1)
        val releaseClose = CountDownLatch(1)
        val factoryClosed = CountDownLatch(1)
        val engine: PlayerEngine = mockk {
            every { events } returns emptyFlow()
            every { close() } answers {
                closeStarted.countDown()
                releaseClose.await(5, TimeUnit.SECONDS)
            }
        }
        val factory: PlayerEngineFactory = mockk {
            every { create() } returns engine
            every { close() } answers { factoryClosed.countDown() }
        }
        mockkObject(PlayerEngines)
        every { PlayerEngines.factory(any()) } returns factory
        val runtime = JdaPlayerRuntime()
        val guild: Guild = mockk {
            every { idLong } returns 42L
        }
        runtime.engineFor(guild)
        val step = defaultBootstrapCleanupSteps(
            BootstrapCleanupTargets(jda = null, router = null, shutdownHook = null, playerRuntime = runtime),
        ).single()
        val finished = CountDownLatch(1)
        val cleanupThread = Thread({
            try {
                step.run()
            } finally {
                finished.countDown()
            }
        }, "player-runtime-cleanup")
        try {
            cleanupThread.start()
            assertTrue(closeStarted.await(2, TimeUnit.SECONDS), "player runtime shutdown never started")

            // The step is still awaiting the runtime and has not closed the shared factory.
            assertEquals(1, factoryClosed.count)

            releaseClose.countDown()
            assertTrue(finished.await(2, TimeUnit.SECONDS), "player runtime cleanup was not awaited")
            assertEquals(0, factoryClosed.count)
        } finally {
            releaseClose.countDown()
            cleanupThread.join(5_000)
            unmockkObject(PlayerEngines)
        }
    }

    @Test
    fun `Given a client without guilds When disconnecting voice Then production cleanup still shuts JDA down`() {
        val client = mockk<JDA>()
        every { client.status } returns JDA.Status.CONNECTED
        every { client.guilds } returns emptyList()
        every { client.shutdown() } just Runs
        every { client.awaitShutdown(10L, TimeUnit.SECONDS) } returns true

        val report = runBootstrapCleanup(
            defaultBootstrapCleanupSteps(
                BootstrapCleanupTargets(jda = client, router = null, shutdownHook = null),
            ),
        )

        assertFalse(report.failed)
        verify(exactly = 1) { client.shutdown() }
    }

    @Test
    fun `Given an already shutdown client When shutdownJda runs Then no shutdown attempt is made`() {
        val client = mockk<JDA>()
        every { client.status } returns JDA.Status.SHUTDOWN

        shutdownJda(client, timeoutSeconds = 0)

        verify(exactly = 0) { client.shutdown() }
        verify(exactly = 0) { client.awaitShutdown(any<Long>(), any()) }
        verify(exactly = 0) { client.shutdownNow() }
    }

    @Test
    fun `Given a client that stops in time When shutdownJda runs Then await is bounded and shutdownNow is not used`() {
        val client = mockk<JDA>()
        every { client.status } returns JDA.Status.CONNECTED
        every { client.shutdown() } just Runs
        every { client.awaitShutdown(0L, TimeUnit.SECONDS) } returns true

        shutdownJda(client, timeoutSeconds = 0)

        verifyOrder {
            client.shutdown()
            client.awaitShutdown(0L, TimeUnit.SECONDS)
        }
        verify(exactly = 0) { client.shutdownNow() }
    }

    @Test
    fun `Given a client that does not stop in time When shutdownJda runs Then force shutdownNow`() {
        val client = mockk<JDA>()
        every { client.status } returns JDA.Status.CONNECTED
        every { client.shutdown() } just Runs
        every { client.awaitShutdown(0L, TimeUnit.SECONDS) } returns false
        every { client.shutdownNow() } just Runs

        shutdownJda(client, timeoutSeconds = 0)

        verifyOrder {
            client.shutdown()
            client.awaitShutdown(0L, TimeUnit.SECONDS)
            client.shutdownNow()
        }
    }
}
