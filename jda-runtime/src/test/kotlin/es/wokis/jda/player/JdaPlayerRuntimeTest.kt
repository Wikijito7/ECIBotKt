package es.wokis.jda.player

import es.wokis.player.engine.PlayerEngine
import es.wokis.player.engine.PlayerEngineFactory
import es.wokis.player.engine.PlayerEngines
import es.wokis.player.engine.audio.ContinuousPlayerAudioStream
import es.wokis.player.engine.audio.PlayerAudioState
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
import net.dv8tion.jda.api.audio.AudioSendHandler
import net.dv8tion.jda.api.audio.hooks.ConnectionStatus
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel
import net.dv8tion.jda.api.managers.AudioManager
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class JdaPlayerRuntimeTest {

    @Test
    fun `Given an active runtime When close signals Then engines and factory close and new access is rejected`() = runTest {
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

            runtime.close()

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
            val firstAdapter = runtime.voiceAdapterFor(firstGuild)
            val secondAdapter = runtime.voiceAdapterFor(secondGuild)
            val firstAgain = runtime.voiceAdapterFor(firstGuild)
            val secondAgain = runtime.voiceAdapterFor(secondGuild)
            runtime.shutdown()

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

            runtime.close()
            assertFailsWith<IllegalStateException> { runtime.engineFor(guild) }
            runtime.shutdown()

            verify(exactly = 1) { engine.close() }
            verify(atLeast = 1) { factory.close() }
        } finally {
            scope.cancel()
            unmockkObject(PlayerEngines)
        }
    }

    @Test
    fun `Given engine and factory close failures When shutdown runs Then both failures are reported`() = runTest {
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

            val thrown = assertFailsWith<IllegalStateException> {
                runtime.shutdown()
            }

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
            val firstEngine = runtime.engineFor(guild)
            val secondEngine = runtime.engineFor(guild)
            val firstAdapter = runtime.voiceAdapterFor(guild)
            val secondAdapter = runtime.voiceAdapterFor(guild)
            runtime.shutdown()
            runtime.shutdown()

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

    @Test
    fun `Given a connected guild player When runtime reconnect runs Then delegate to the adapter and reopen`() = runTest {
        val firstStream = FakeRuntimeStream()
        val secondStream = FakeRuntimeStream()
        var streamCalls = 0
        val engine: PlayerEngine = mockk {
            every { events } returns emptyFlow()
            every { audioStream() } answers {
                if (streamCalls++ == 0) firstStream else secondStream
            }
            justRun { close() }
        }
        val factory: PlayerEngineFactory = mockk {
            every { create() } returns engine
            justRun { close() }
        }
        mockkObject(PlayerEngines)
        every { PlayerEngines.factory(any()) } returns factory
        var connected = false
        val audioManager: AudioManager = mockk()
        val channel: VoiceChannel = mockk {
            every { idLong } returns 42L
        }
        val guild: Guild = mockk {
            every { idLong } returns 42L
        }
        every { guild.audioManager } returns audioManager
        every { audioManager.isConnected } answers { connected }
        every { audioManager.connectionStatus } answers {
            if (connected) ConnectionStatus.CONNECTED else ConnectionStatus.NOT_CONNECTED
        }
        every { audioManager.setSendingHandler(any<AudioSendHandler>()) } returns Unit
        every { audioManager.setSendingHandler(null) } returns Unit
        every { audioManager.openAudioConnection(channel) } answers { connected = true }
        every { audioManager.closeAudioConnection() } answers { connected = false }
        val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))
        val runtime = JdaPlayerRuntime(
            scope = scope,
            serialDispatcherFactory = { UnconfinedTestDispatcher(testScheduler) },
        )
        try {
            assertTrue(runtime.connect(guild, channel), "the initial connect must open a session")

            val result = runtime.reconnect(guild, channel)

            assertEquals(JdaPlayerReconnectResult.Reconnected, result)
            verify(exactly = 2) { audioManager.openAudioConnection(channel) }
            verify(exactly = 1) { audioManager.closeAudioConnection() }
            assertTrue(firstStream.closeCalls > 0, "the first stream must be closed before reopening")
        } finally {
            scope.cancel()
            unmockkObject(PlayerEngines)
        }
    }

    private class FakeRuntimeStream : ContinuousPlayerAudioStream {
        var closeCalls = 0
            private set

        override fun next(): PlayerAudioState = PlayerAudioState.PausedSilence

        override fun close() {
            closeCalls++
        }
    }
}
