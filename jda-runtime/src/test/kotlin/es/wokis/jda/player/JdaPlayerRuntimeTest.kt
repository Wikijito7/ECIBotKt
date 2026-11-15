package es.wokis.jda.player

import es.wokis.player.engine.PlayerEngine
import es.wokis.player.engine.PlayerEngineFactory
import es.wokis.player.engine.PlayerEngines
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import net.dv8tion.jda.api.entities.Guild
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame
import kotlin.test.assertSame

@OptIn(ExperimentalCoroutinesApi::class)
class JdaPlayerRuntimeTest {

    @Test
    fun `Given an active runtime When close signals Then engines and factory close and new access is rejected`() = runTest {
        // Given
        val engine: PlayerEngine = mockk {
            every { events } returns emptyFlow()
            justRun { close() }
        }
        val factory: PlayerEngineFactory = mockk {
            every { create() } returns engine
            justRun { close() }
        }
        mockkObject(PlayerEngines)
        every { PlayerEngines.factory(any()) } returns factory
        val guild: Guild = mockk {
            every { idLong } returns 42L
        }
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val runtime = JdaPlayerRuntime(
            scope = scope,
            serialDispatcherFactory = { UnconfinedTestDispatcher(testScheduler) },
        )
        try {
            runtime.engineFor(guild)
            runtime.voiceAdapterFor(guild)

            // When
            runtime.close()

            // Then
            verify(exactly = 1) { engine.close() }
            verify(exactly = 1) { factory.close() }
            assertFailsWith<IllegalStateException> { runtime.engineFor(guild) }
            assertFailsWith<IllegalStateException> { runtime.voiceAdapterFor(guild) }
        } finally {
            scope.cancel()
            unmockkObject(PlayerEngines)
        }
    }

    @Test
    fun `Given two guild adapters When requested repeatedly Then one dispatcher per guild is created and retained`() = runTest {
        // Given
        val engine: PlayerEngine = mockk {
            every { events } returns emptyFlow()
            justRun { close() }
        }
        val factory: PlayerEngineFactory = mockk {
            every { create() } returns engine
            justRun { close() }
        }
        mockkObject(PlayerEngines)
        every { PlayerEngines.factory(any()) } returns factory
        val firstGuild: Guild = mockk {
            every { idLong } returns 41L
        }
        val secondGuild: Guild = mockk {
            every { idLong } returns 42L
        }
        val dispatchers = mutableListOf<CoroutineDispatcher>()
        val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))
        val runtime = JdaPlayerRuntime(
            scope = scope,
            serialDispatcherFactory = {
                UnconfinedTestDispatcher(testScheduler).also { dispatchers += it }
            },
        )
        try {
            // When
            val firstAdapter = runtime.voiceAdapterFor(firstGuild)
            val secondAdapter = runtime.voiceAdapterFor(secondGuild)
            val firstAgain = runtime.voiceAdapterFor(firstGuild)
            val secondAgain = runtime.voiceAdapterFor(secondGuild)
            runtime.shutdown()

            // Then
            assertSame(firstAdapter, firstAgain)
            assertSame(secondAdapter, secondAgain)
            assertEquals(2, dispatchers.size)
            assertNotSame(dispatchers[0], dispatchers[1])
        } finally {
            scope.cancel()
            unmockkObject(PlayerEngines)
        }
    }

    @Test
    fun `Given a fast close When shutdown follows Then awaited cleanup remains callable`() = runTest {
        // Given
        val engine: PlayerEngine = mockk {
            every { events } returns emptyFlow()
            justRun { close() }
        }
        val factory: PlayerEngineFactory = mockk {
            every { create() } returns engine
            justRun { close() }
        }
        mockkObject(PlayerEngines)
        every { PlayerEngines.factory(any()) } returns factory
        val guild: Guild = mockk {
            every { idLong } returns 42L
        }
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val runtime = JdaPlayerRuntime(
            scope = scope,
            serialDispatcherFactory = { UnconfinedTestDispatcher(testScheduler) },
        )
        try {
            runtime.engineFor(guild)
            runtime.voiceAdapterFor(guild)

            // When
            runtime.close()
            assertFailsWith<IllegalStateException> { runtime.engineFor(guild) }
            runtime.shutdown()

            // Then
            verify(exactly = 1) { engine.close() }
            verify(atLeast = 1) { factory.close() }
        } finally {
            scope.cancel()
            unmockkObject(PlayerEngines)
        }
    }

    @Test
    fun `Given engine and factory close failures When shutdown runs Then both failures are reported`() = runTest {
        // Given
        val engineFailure = IllegalStateException("engine close failed")
        val factoryFailure = IllegalStateException("factory close failed")
        val engine: PlayerEngine = mockk {
            every { events } returns emptyFlow()
            every { close() } throws engineFailure
        }
        val factory: PlayerEngineFactory = mockk {
            every { create() } returns engine
            every { close() } throws factoryFailure
        }
        mockkObject(PlayerEngines)
        every { PlayerEngines.factory(any()) } returns factory
        val guild: Guild = mockk {
            every { idLong } returns 42L
        }
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val runtime = JdaPlayerRuntime(
            scope = scope,
            serialDispatcherFactory = { UnconfinedTestDispatcher(testScheduler) },
        )
        try {
            runtime.engineFor(guild)

            // When
            val thrown = assertFailsWith<IllegalStateException> {
                runtime.shutdown()
            }

            // Then
            assertSame(engineFailure, thrown)
            assertEquals(listOf(factoryFailure), thrown.suppressed.toList())
            verify(exactly = 1) { factory.close() }
        } finally {
            scope.cancel()
            unmockkObject(PlayerEngines)
        }
    }

    @Test
    fun `Given a guild requested twice When shutdown runs twice Then engines and the shared factory close once`() = runTest {
        // Given
        val engine: PlayerEngine = mockk {
            every { events } returns emptyFlow()
            justRun { close() }
        }
        val factory: PlayerEngineFactory = mockk {
            every { create() } returns engine
            justRun { close() }
        }
        mockkObject(PlayerEngines)
        every { PlayerEngines.factory(any()) } returns factory
        val guild: Guild = mockk {
            every { idLong } returns 42L
        }
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val runtime = JdaPlayerRuntime(
            scope = scope,
            serialDispatcherFactory = { UnconfinedTestDispatcher(testScheduler) },
        )
        try {
            // When
            val firstEngine = runtime.engineFor(guild)
            val secondEngine = runtime.engineFor(guild)
            val firstAdapter = runtime.voiceAdapterFor(guild)
            val secondAdapter = runtime.voiceAdapterFor(guild)
            runtime.shutdown()
            runtime.shutdown()

            // Then
            assertSame(firstEngine, secondEngine)
            assertSame(firstAdapter, secondAdapter)
            verify(exactly = 1) { factory.create() }
            verify(exactly = 1) { engine.close() }
            verify(exactly = 1) { factory.close() }
        } finally {
            scope.cancel()
            unmockkObject(PlayerEngines)
        }
    }
}
