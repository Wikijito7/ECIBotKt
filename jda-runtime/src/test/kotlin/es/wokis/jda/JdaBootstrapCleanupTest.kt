package es.wokis.jda

import es.wokis.domain.model.GuildId
import es.wokis.jda.command.JdaCommandRouter
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.isActive
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.audio.AudioModuleConfig
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.SelfUser
import net.dv8tion.jda.api.interactions.commands.Command
import net.dv8tion.jda.api.interactions.commands.build.CommandData
import net.dv8tion.jda.api.managers.AudioManager
import net.dv8tion.jda.api.requests.RestAction
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit
import java.util.function.Consumer

class JdaBootstrapCleanupTest {

    private val config = JdaRuntimeConfig(
        token = "fake-token",
        guildId = GuildId(987654321uL),
    )

    @Test
    fun `Given every step succeeds When cleanup runs Then the report is clean`() {
        // Given
        val events = mutableListOf<String>()
        val steps = listOf(
            BootstrapCleanupStep("one") { events += "one" },
            BootstrapCleanupStep("two") { events += "two" },
        )

        // When
        val report = runBootstrapCleanup(steps)

        // Then
        assertEquals(listOf("one", "two"), events)
        assertFalse(report.failed)
        assertNull(report.failure)
        assertNull(report.fatalFailure)
    }

    @Test
    fun `Given a failing step When cleanup runs Then every later step is still attempted in order`() {
        // Given
        val events = mutableListOf<String>()
        val failure = IllegalStateException("voice disconnect failed")
        val steps = listOf(
            BootstrapCleanupStep("one") { events += "one" },
            BootstrapCleanupStep("two") {
                events += "two"
                throw failure
            },
            BootstrapCleanupStep("three") { events += "three" },
        )

        // When
        val report = runBootstrapCleanup(steps)

        // Then
        assertEquals(listOf("one", "two", "three"), events)
        assertTrue(report.failed)
        assertSame(failure, report.failure)
        assertNull(report.fatalFailure)
    }

    @Test
    fun `Given several failures When cleanup runs Then none are thrown and all are attached in order`() {
        // Given
        val first = IllegalStateException("first")
        val second = IllegalArgumentException("second")
        val steps = listOf(
            BootstrapCleanupStep("first") { throw first },
            BootstrapCleanupStep("second") { throw second },
        )

        // When
        val report = assertDoesNotThrow<BootstrapCleanupReport> { runBootstrapCleanup(steps) }

        // Then
        assertSame(first, report.failure)
        assertNull(report.fatalFailure)
        val primary = RuntimeException("primary startup failure")
        report.attachTo(primary)
        assertEquals(listOf(first, second), primary.suppressed.toList())
    }

    @Test
    fun `Given an Error When cleanup runs Then it is collected as the fatal failure without being thrown`() {
        // Given
        val error = AssertionError("native error")
        val steps = listOf(
            BootstrapCleanupStep("fatal") { throw error },
            BootstrapCleanupStep("after") { },
        )

        // When
        val report = assertDoesNotThrow<BootstrapCleanupReport> { runBootstrapCleanup(steps) }

        // Then
        assertSame(error, report.failure)
        assertSame(error, report.fatalFailure)
    }

    @Test
    fun `Given a normal failure before a cancellation When cleanup runs Then the cancellation is the fatal failure`() {
        // Given
        val normal = IllegalStateException("normal")
        val cancellation = CancellationException("cancelled")
        val steps = listOf(
            BootstrapCleanupStep("normal") { throw normal },
            BootstrapCleanupStep("cancel") { throw cancellation },
        )

        // When
        val report = runBootstrapCleanup(steps)

        // Then
        assertSame(normal, report.failure)
        assertSame(cancellation, report.fatalFailure)
    }

    @Test
    fun `Given a primary failure When cleanup failures are attached twice Then each is suppressed once and never itself`() {
        // Given: the first cleanup failure is the same instance as the primary.
        val primary = IllegalStateException("primary")
        val other = IllegalArgumentException("other")
        val report = runBootstrapCleanup(
            listOf(
                BootstrapCleanupStep("primary") { throw primary },
                BootstrapCleanupStep("other") { throw other },
            ),
        )

        // When
        report.attachTo(primary)
        report.attachTo(primary)

        // Then: the primary is skipped and the duplicate attachment is idempotent.
        assertEquals(listOf(other), primary.suppressed.toList())
    }

    @Test
    fun `Given no startup failure and a clean cleanup When finishing Then exit success`() {
        // When
        val exitCode = finishBootstrap(null, BootstrapCleanupReport(emptyList()))

        // Then
        assertEquals(0, exitCode)
    }

    @Test
    fun `Given a primary startup failure When cleanup succeeds Then preserve the failure and exit failed`() {
        // Given
        val primary = IllegalStateException("startup failed")

        // When
        val exitCode = finishBootstrap(primary, BootstrapCleanupReport(emptyList()))

        // Then
        assertEquals(1, exitCode)
        assertTrue(primary.suppressed.isEmpty())
    }

    @Test
    fun `Given a primary startup failure When cleanup also fails Then attach every cleanup failure and exit failed`() {
        // Given
        val primary = IllegalStateException("startup failed")
        val cleanup = IllegalArgumentException("cleanup failed")
        val secondCleanup = RuntimeException("second cleanup failed")

        // When
        val exitCode = finishBootstrap(primary, BootstrapCleanupReport(listOf(cleanup, secondCleanup)))

        // Then
        assertEquals(1, exitCode)
        assertEquals(listOf(cleanup, secondCleanup), primary.suppressed.toList())
    }

    @Test
    fun `Given a fatal primary failure When finishing Then rethrow it with cleanup failures suppressed`() {
        // Given
        val primary = AssertionError("fatal startup")
        val cleanup = IllegalStateException("cleanup failed")

        // When
        val thrown = assertThrows(AssertionError::class.java) {
            finishBootstrap(primary, BootstrapCleanupReport(listOf(cleanup)))
        }

        // Then
        assertSame(primary, thrown)
        assertEquals(listOf(cleanup), primary.suppressed.toList())
    }

    @Test
    fun `Given a cancellation as the primary failure When finishing Then rethrow the cancellation`() {
        // Given
        val cancellation = CancellationException("stopping")

        // When
        val thrown = assertThrows(CancellationException::class.java) {
            finishBootstrap(cancellation, BootstrapCleanupReport(emptyList()))
        }

        // Then
        assertSame(cancellation, thrown)
    }

    @Test
    fun `Given a successful startup When cleanup fails Then exit failed with the first failure reported`() {
        // Given
        val cleanup = IllegalStateException("cleanup failed")
        val secondCleanup = IllegalArgumentException("second cleanup failed")

        // When
        val exitCode = finishBootstrap(null, BootstrapCleanupReport(listOf(cleanup, secondCleanup)))

        // Then
        assertEquals(1, exitCode)
        assertEquals(listOf(secondCleanup), cleanup.suppressed.toList())
    }

    @Test
    fun `Given a successful startup When cleanup fails fatally Then throw the fatal cleanup failure`() {
        // Given
        val fatal = AssertionError("fatal cleanup")
        val secondary = IllegalStateException("secondary cleanup")

        // When
        val thrown = assertThrows(AssertionError::class.java) {
            finishBootstrap(null, BootstrapCleanupReport(listOf(secondary, fatal)))
        }

        // Then
        assertSame(fatal, thrown)
        assertEquals(listOf(secondary), fatal.suppressed.toList())
    }

    @Test
    fun `Given a normal startup failure When cleanup fails fatally Then the fatal cleanup failure wins with the primary suppressed`() {
        // Given
        val primary = IllegalStateException("startup failed")
        val fatal = AssertionError("fatal cleanup")

        // When
        val thrown = assertThrows(AssertionError::class.java) {
            finishBootstrap(primary, BootstrapCleanupReport(listOf(fatal)))
        }

        // Then
        assertSame(fatal, thrown)
        assertEquals(listOf(primary), fatal.suppressed.toList())
    }

    @Test
    fun `Given every target When production cleanup runs Then voice disconnects before router hook and JDA shutdown`() {
        // Given
        val events = mutableListOf<String>()
        val audioManager = mockk<AudioManager>()
        val guild = mockk<Guild>()
        val client = mockk<JDA>()
        val runtime = mockk<Runtime>()
        val hook = Thread({ }, "test-shutdown-hook")
        val router = AutoCloseable { events += "command-router" }

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

            // When
            val steps = defaultBootstrapCleanupSteps(
                BootstrapCleanupTargets(jda = client, router = router, shutdownHook = hook),
            )
            steps.forEach { it.run() }
        } finally {
            unmockkStatic(Runtime::class)
        }

        // Then
        assertEquals(
            listOf("voice-disconnect", "command-router", "shutdown-hook", "jda-shutdown"),
            events,
        )
    }

    @Test
    fun `Given a voice disconnect failure When production cleanup runs Then router hook and JDA shutdown still run`() {
        // Given
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

            // When
            val report = runBootstrapCleanup(
                defaultBootstrapCleanupSteps(
                    BootstrapCleanupTargets(jda = client, router = router, shutdownHook = hook),
                ),
            )

            // Then
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
        // Then
        assertTrue(defaultBootstrapCleanupSteps(BootstrapCleanupTargets(null, null, null)).isEmpty())

        val router = AutoCloseable { }
        val routerOnly = defaultBootstrapCleanupSteps(
            BootstrapCleanupTargets(jda = null, router = router, shutdownHook = null),
        )
        assertEquals(listOf("command-router"), routerOnly.map { it.name })
    }

    @Test
    fun `Given an already shutdown client When shutdownJda runs Then no shutdown attempt is made`() {
        // Given
        val client = mockk<JDA>()
        every { client.status } returns JDA.Status.SHUTDOWN

        // When
        shutdownJda(client, timeoutSeconds = 0)

        // Then
        verify(exactly = 0) { client.shutdown() }
        verify(exactly = 0) { client.awaitShutdown(any<Long>(), any()) }
        verify(exactly = 0) { client.shutdownNow() }
    }

    @Test
    fun `Given a client that stops in time When shutdownJda runs Then await is bounded and shutdownNow is not used`() {
        // Given
        val client = mockk<JDA>()
        every { client.status } returns JDA.Status.CONNECTED
        every { client.shutdown() } just Runs
        every { client.awaitShutdown(0L, TimeUnit.SECONDS) } returns true

        // When
        shutdownJda(client, timeoutSeconds = 0)

        // Then
        verifyOrder {
            client.shutdown()
            client.awaitShutdown(0L, TimeUnit.SECONDS)
        }
        verify(exactly = 0) { client.shutdownNow() }
    }

    @Test
    fun `Given a client that does not stop in time When shutdownJda runs Then force shutdownNow`() {
        // Given
        val client = mockk<JDA>()
        every { client.status } returns JDA.Status.CONNECTED
        every { client.shutdown() } just Runs
        every { client.awaitShutdown(0L, TimeUnit.SECONDS) } returns false
        every { client.shutdownNow() } just Runs

        // When
        shutdownJda(client, timeoutSeconds = 0)

        // Then
        verifyOrder {
            client.shutdown()
            client.awaitShutdown(0L, TimeUnit.SECONDS)
            client.shutdownNow()
        }
    }

    @Test
    fun `Given a failing cleanup step factory When bootstrapping fails Then report failure without swallowing the factory error`() {
        // Given
        val preflight = DavePreflight { throw IllegalStateException("DAVE is unavailable") }
        val clientFactory = JdaClientFactory { _, _ ->
            error("a client must never be built after a failed preflight")
        }
        var observedTargets: BootstrapCleanupTargets? = null

        // When
        val exitCode = runBootstrap(config, preflight, clientFactory) { targets ->
            observedTargets = targets
            throw IllegalStateException("cleanup factory failed")
        }

        // Then
        assertEquals(1, exitCode)
        assertEquals(BootstrapCleanupTargets(null, null, null), observedTargets)
    }

    @Test
    fun `Given a fatal startup failure When the cleanup step factory also fails Then rethrow the fatal failure`() {
        // Given
        val fatal = AssertionError("fatal startup")
        val preflight = DavePreflight { throw fatal }
        val clientFactory = JdaClientFactory { _, _ ->
            error("a client must never be built after a failed preflight")
        }

        // When
        val thrown = assertThrows(AssertionError::class.java) {
            runBootstrap(config, preflight, clientFactory) {
                throw IllegalStateException("cleanup factory failed")
            }
        }

        // Then
        assertSame(fatal, thrown)
        assertEquals("cleanup factory failed", thrown.suppressed.single().message)
    }

    @Test
    fun `Given a client without guilds When disconnecting voice Then production cleanup still shuts JDA down`() {
        // Given
        val client = mockk<JDA>()
        every { client.status } returns JDA.Status.CONNECTED
        every { client.guilds } returns emptyList()
        every { client.shutdown() } just Runs
        every { client.awaitShutdown(10L, TimeUnit.SECONDS) } returns true

        // When
        val report = runBootstrapCleanup(
            defaultBootstrapCleanupSteps(
                BootstrapCleanupTargets(jda = client, router = null, shutdownHook = null),
            ),
        )

        // Then
        assertFalse(report.failed)
        verify(exactly = 1) { client.shutdown() }
    }

    @Test
    fun `Given the JVM is already shutting down When the hook is removed Then the IllegalStateException is benign`() {
        // Given: the JDK throws IllegalStateException when shutdown is in progress.
        // The message below is representative of the current JDK wording, not a documented contract.
        // The hook removal must tolerate it without matching on the message text.
        val runtime = mockk<Runtime>()
        val hook = Thread({ }, "test-shutdown-hook")
        val shutdownInProgress = IllegalStateException("Shutdown in progress")
        mockkStatic(Runtime::class)
        try {
            every { Runtime.getRuntime() } returns runtime
            every { runtime.removeShutdownHook(hook) } throws shutdownInProgress

            // When
            assertDoesNotThrow { removeShutdownHook(hook) }

            // Then
            verify(exactly = 1) { runtime.removeShutdownHook(hook) }
        } finally {
            unmockkStatic(Runtime::class)
        }
    }

    @Test
    fun `Given a shutdown-in-progress IllegalStateException with an arbitrary message When the hook is removed Then it is benign`() {
        // Given: any documented IllegalStateException is benign, so the message must not be matched.
        val runtime = mockk<Runtime>()
        val hook = Thread({ }, "test-shutdown-hook")
        val arbitrary = IllegalStateException("Runtime is shutting down, hooks are frozen")
        mockkStatic(Runtime::class)
        try {
            every { Runtime.getRuntime() } returns runtime
            every { runtime.removeShutdownHook(hook) } throws arbitrary

            // When
            assertDoesNotThrow { removeShutdownHook(hook) }

            // Then
            verify(exactly = 1) { runtime.removeShutdownHook(hook) }
        } finally {
            unmockkStatic(Runtime::class)
        }
    }

    @Test
    fun `Given a shutdown-in-progress IllegalStateException without a message When the hook is removed Then it is benign`() {
        // Given
        val runtime = mockk<Runtime>()
        val hook = Thread({ }, "test-shutdown-hook")
        val withoutMessage = IllegalStateException()
        mockkStatic(Runtime::class)
        try {
            every { Runtime.getRuntime() } returns runtime
            every { runtime.removeShutdownHook(hook) } throws withoutMessage

            // When
            assertDoesNotThrow { removeShutdownHook(hook) }

            // Then
            verify(exactly = 1) { runtime.removeShutdownHook(hook) }
        } finally {
            unmockkStatic(Runtime::class)
        }
    }

    @Test
    fun `Given a SecurityException When the hook is removed Then it is surfaced`() {
        // Given: only IllegalStateException is benign; a security failure is a real cleanup failure.
        val runtime = mockk<Runtime>()
        val hook = Thread({ }, "test-shutdown-hook")
        val securityFailure = SecurityException("hook removal is not allowed")
        mockkStatic(Runtime::class)
        try {
            every { Runtime.getRuntime() } returns runtime
            every { runtime.removeShutdownHook(hook) } throws securityFailure

            // When
            val thrown = assertThrows(SecurityException::class.java) { removeShutdownHook(hook) }

            // Then
            assertSame(securityFailure, thrown)
            verify(exactly = 1) { runtime.removeShutdownHook(hook) }
        } finally {
            unmockkStatic(Runtime::class)
        }
    }

    @Test
    fun `Given another runtime exception When the hook is removed Then it is surfaced`() {
        // Given: only IllegalStateException is swallowed, so an unrelated failure must not be hidden.
        val runtime = mockk<Runtime>()
        val hook = Thread({ }, "test-shutdown-hook")
        val unrelatedFailure = NullPointerException("hook is not registered")
        mockkStatic(Runtime::class)
        try {
            every { Runtime.getRuntime() } returns runtime
            every { runtime.removeShutdownHook(hook) } throws unrelatedFailure

            // When
            val thrown = assertThrows(NullPointerException::class.java) { removeShutdownHook(hook) }

            // Then
            assertSame(unrelatedFailure, thrown)
            verify(exactly = 1) { runtime.removeShutdownHook(hook) }
        } finally {
            unmockkStatic(Runtime::class)
        }
    }

    @Test
    fun `Given an Error When the hook is removed Then it is surfaced`() {
        // Given
        val runtime = mockk<Runtime>()
        val hook = Thread({ }, "test-shutdown-hook")
        val error = AssertionError("hook removal failed fatally")
        mockkStatic(Runtime::class)
        try {
            every { Runtime.getRuntime() } returns runtime
            every { runtime.removeShutdownHook(hook) } throws error

            // When
            val thrown = assertThrows(AssertionError::class.java) { removeShutdownHook(hook) }

            // Then
            assertSame(error, thrown)
            verify(exactly = 1) { runtime.removeShutdownHook(hook) }
        } finally {
            unmockkStatic(Runtime::class)
        }
    }

    @Test
    fun `Given a failing cleanup factory and created resources When startup succeeded Then the fallback cleans everything and fails`() {
        // Given: startup reaches a clean shutdown, so every cleanup target is non-null.
        val fixture = BootstrapFixture(config)

        // When
        val exitCode = fixture.run {
            throw IllegalStateException("cleanup factory failed")
        }

        // Then: the factory failure is reported as a failed startup...
        assertEquals(1, exitCode)
        val observedRouter = requireNotNull(fixture.observedRouter) {
            "the router created by the bootstrap must reach cleanup"
        }
        assertTrue(observedRouter.isClosed(), "the fallback must close the command router")
        assertSame(fixture.shutdownHook, fixture.observedHook, "the created hook must still be removed")
        assertEquals(
            listOf("voice-disconnect", "shutdown-hook-removed", "jda-shutdown"),
            fixture.events.dropWhile { it != "voice-disconnect" },
            "the fallback must attempt voice, hook and JDA cleanup in production order",
        )
        verify(exactly = 1) { fixture.audioManager.closeAudioConnection() }
        verify(exactly = 1) { fixture.jda.shutdown() }
    }

    @Test
    fun `Given a cleanup factory that throws an Error When startup succeeded Then the fallback runs and the Error is rethrown`() {
        // Given
        val fixture = BootstrapFixture(config)
        val factoryFailure = AssertionError("fatal cleanup factory")

        // When
        val thrown = assertThrows(AssertionError::class.java) {
            fixture.run { throw factoryFailure }
        }

        // Then
        assertSame(factoryFailure, thrown)
        assertTrue(factoryFailure.suppressed.isEmpty(), "a clean fallback must not add suppressions")
        assertEquals(
            listOf("voice-disconnect", "shutdown-hook-removed", "jda-shutdown"),
            fixture.events.dropWhile { it != "voice-disconnect" },
        )
    }

    @Test
    fun `Given a cleanup factory that throws a cancellation When startup succeeded Then the fallback runs and the cancellation is rethrown`() {
        // Given
        val fixture = BootstrapFixture(config)
        val factoryFailure = CancellationException("cleanup factory cancelled")

        // When
        val thrown = assertThrows(CancellationException::class.java) {
            fixture.run { throw factoryFailure }
        }

        // Then
        assertSame(factoryFailure, thrown)
        assertEquals(
            listOf("voice-disconnect", "shutdown-hook-removed", "jda-shutdown"),
            fixture.events.dropWhile { it != "voice-disconnect" },
        )
    }

    @Test
    fun `Given a normal startup failure When the cleanup factory throws an Error Then the Error wins with the primary suppressed`() {
        // Given
        val primary = IllegalStateException("startup failed after resources were created")
        val fixture = BootstrapFixture(config, awaitReadyFailure = primary)
        val factoryFailure = AssertionError("fatal cleanup factory")

        // When
        val thrown = assertThrows(AssertionError::class.java) {
            fixture.run { throw factoryFailure }
        }

        // Then
        assertSame(factoryFailure, thrown)
        assertEquals(listOf(primary), factoryFailure.suppressed.toList())
        assertTrue(fixture.events.contains("voice-disconnect"), "the fallback must still clean the created resources")
    }

    @Test
    fun `Given a normal startup failure When the cleanup factory throws a cancellation Then the cancellation wins with the primary suppressed`() {
        // Given
        val primary = IllegalStateException("startup failed after resources were created")
        val fixture = BootstrapFixture(config, awaitReadyFailure = primary)
        val factoryFailure = CancellationException("cleanup factory cancelled")

        // When
        val thrown = assertThrows(CancellationException::class.java) {
            fixture.run { throw factoryFailure }
        }

        // Then
        assertSame(factoryFailure, thrown)
        assertEquals(listOf(primary), factoryFailure.suppressed.toList())
    }

    @Test
    fun `Given a fatal startup failure When the cleanup factory also throws an Error Then the primary keeps precedence`() {
        // Given
        val primary = AssertionError("fatal startup after resources were created")
        val fixture = BootstrapFixture(config, awaitReadyFailure = primary)
        val factoryFailure = AssertionError("fatal cleanup factory")

        // When
        val thrown = assertThrows(AssertionError::class.java) {
            fixture.run { throw factoryFailure }
        }

        // Then
        assertSame(primary, thrown)
        assertEquals(listOf(factoryFailure), primary.suppressed.toList())
        assertTrue(fixture.events.contains("voice-disconnect"), "the fallback must still clean the created resources")
    }

    private fun JdaCommandRouter.isClosed(): Boolean {
        val scope = JdaCommandRouter::class.java
            .getDeclaredField("scope")
            .apply { isAccessible = true }
            .get(this) as CoroutineScope
        return !scope.isActive
    }

    private class BootstrapFixture(
        private val config: JdaRuntimeConfig,
        awaitReadyFailure: Throwable? = null,
    ) {
        val events = mutableListOf<String>()
        val jda: JDA = mockk()
        val guild: Guild = mockk()
        val audioManager: AudioManager = mockk()
        val runtime: Runtime = mockk()
        val selfUser: SelfUser = mockk()
        private val upsertAction: RestAction<Command> = mockk()

        lateinit var shutdownHook: Thread
            private set

        var observedHook: Thread? = null
            private set

        var observedRouter: JdaCommandRouter? = null
            private set

        init {
            every { jda.addEventListener(*anyVararg()) } just Runs
            every { jda.selfUser } returns selfUser
            every { selfUser.name } returns "Fixture Bot"
            every { jda.getGuildById(config.guildId.value.toLong()) } returns guild
            every { guild.upsertCommand(any<CommandData>()) } returns upsertAction
            every { upsertAction.queue(any(), any()) } answers {
                events += "command-registered"
                firstArg<Consumer<Command?>>().accept(null)
            }
            every { jda.awaitShutdown() } answers {
                events += "await-shutdown"
                true
            }
            every { jda.status } returns JDA.Status.CONNECTED
            every { jda.guilds } returns listOf(guild)
            every { guild.audioManager } returns audioManager
            every { audioManager.isConnected } returns true
            every { audioManager.closeAudioConnection() } answers { events += "voice-disconnect" }
            every { jda.shutdown() } answers { events += "jda-shutdown" }
            every { jda.awaitShutdown(10L, TimeUnit.SECONDS) } returns true

            if (awaitReadyFailure == null) {
                every { jda.awaitReady() } answers {
                    events += "ready"
                    jda
                }
            } else {
                every { jda.awaitReady() } throws awaitReadyFailure
            }
        }

        fun run(cleanupStepsFactory: (BootstrapCleanupTargets) -> List<BootstrapCleanupStep>): Int {
            mockkStatic(Runtime::class)
            try {
                every { Runtime.getRuntime() } returns runtime
                every { runtime.addShutdownHook(any()) } answers {
                    shutdownHook = firstArg()
                    events += "shutdown-hook-added"
                }
                every { runtime.removeShutdownHook(any()) } answers {
                    observedHook = firstArg()
                    events += "shutdown-hook-removed"
                    true
                }

                return runBootstrap(
                    config = config,
                    davePreflight = DavePreflight { AudioModuleConfig() },
                    clientFactory = JdaClientFactory { _, _ -> jda },
                    cleanupStepsFactory = { targets ->
                        observedRouter = targets.router as? JdaCommandRouter
                        cleanupStepsFactory(targets)
                    },
                )
            } finally {
                unmockkStatic(Runtime::class)
            }
        }
    }
}
