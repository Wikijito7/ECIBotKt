package es.wokis.jda.player

import es.wokis.player.engine.PlayerEngine
import es.wokis.player.engine.PlayerEngineFactory
import es.wokis.player.engine.PlayerEngines
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import net.dv8tion.jda.api.entities.Guild
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class JdaPlayerRuntimeTest {

    @Test
    fun `Given an active runtime When close signals Then engines and factory close and new access is rejected`() {
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
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val runtime = JdaPlayerRuntime(scope = scope)
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
    fun `Given a fast close When shutdown follows Then awaited cleanup remains callable`() {
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
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val runtime = JdaPlayerRuntime(scope = scope)
        try {
            runtime.engineFor(guild)
            runtime.voiceAdapterFor(guild)

            // When
            runtime.close()
            assertFailsWith<IllegalStateException> { runtime.engineFor(guild) }
            runBlocking { runtime.shutdown() }

            // Then
            verify(exactly = 1) { engine.close() }
            verify(atLeast = 1) { factory.close() }
        } finally {
            scope.cancel()
            unmockkObject(PlayerEngines)
        }
    }

    @Test
    fun `Given engine and factory close failures When shutdown runs Then both failures are reported`() {
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
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val runtime = JdaPlayerRuntime(scope = scope)
        try {
            runtime.engineFor(guild)

            // When
            val thrown = assertFailsWith<IllegalStateException> {
                runBlocking { runtime.shutdown() }
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
    fun `Given a guild requested twice When shutdown runs twice Then engines and the shared factory close once`() {
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
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val runtime = JdaPlayerRuntime(scope = scope)
        try {
            // When
            val firstEngine = runtime.engineFor(guild)
            val secondEngine = runtime.engineFor(guild)
            val firstAdapter = runtime.voiceAdapterFor(guild)
            val secondAdapter = runtime.voiceAdapterFor(guild)
            runBlocking {
                runtime.shutdown()
                runtime.shutdown()
            }

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
