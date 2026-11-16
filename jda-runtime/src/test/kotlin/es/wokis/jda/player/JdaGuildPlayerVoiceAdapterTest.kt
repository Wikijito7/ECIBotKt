package es.wokis.jda.player

import es.wokis.jda.voice.JdaAudioSendHandler
import es.wokis.player.engine.PlayerEngine
import es.wokis.player.engine.audio.ContinuousPlayerAudioStream
import es.wokis.player.engine.audio.PlayerAudioState
import es.wokis.player.engine.event.PlayerEvent
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import net.dv8tion.jda.api.audio.AudioSendHandler
import net.dv8tion.jda.api.audio.hooks.ConnectionStatus
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel
import net.dv8tion.jda.api.managers.AudioManager
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class JdaGuildPlayerVoiceAdapterTest {

    private val testFixtures = mutableListOf<Fixture>()

    @AfterEach
    fun disposeTestFixtures() {
        testFixtures.forEach { it.dispose() }
        testFixtures.clear()
    }

    @Test
    fun `Given a guild voice channel When connect runs Then the existing send handler is installed`() = runTest {
        val fixture = testFixture(testDispatcher())

        fixture.adapter.connect(fixture.channel)

        assertNotNull(fixture.installedHandler)
        assertTrue(fixture.installedHandler is JdaAudioSendHandler)
        verify(exactly = 1) { fixture.audioManager.openAudioConnection(fixture.channel) }
    }

    @Test
    fun `Given a connected adapter When disconnect runs Then handler and stream are closed`() = runTest {
        val fixture = testFixture(testDispatcher())
        fixture.adapter.connect(fixture.channel)
        clearMocks(fixture.audioManager, answers = false)
        val handler = requireNotNull(fixture.installedHandler) as JdaAudioSendHandler

        fixture.adapter.disconnect()

        assertFalse(handler.canProvide(), "the adapter must close the send handler")
        verify(exactly = 1) { fixture.audioManager.setSendingHandler(null) }
        verify(exactly = 1) { fixture.audioManager.closeAudioConnection() }
        assertTrue(fixture.stream.closeCalls >= 1)
    }

    @Test
    fun `Given a connected adapter When close signals Then JDA cleanup waits for shutdown`() = runTest {
        val fixture = testFixture(testDispatcher())
        fixture.adapter.connect(fixture.channel)
        clearMocks(fixture.audioManager, answers = false)

        fixture.adapter.close()

        // Then: close is only the fast signal and does not touch JDA.
        verify(exactly = 0) { fixture.audioManager.setSendingHandler(null) }
        verify(exactly = 0) { fixture.audioManager.closeAudioConnection() }

        fixture.adapter.shutdown()

        verify(exactly = 1) { fixture.audioManager.setSendingHandler(null) }
        verify(exactly = 1) { fixture.audioManager.closeAudioConnection() }
    }

    @Test
    fun `Given an active adapter When the engine emits stopped Then the voice connection disconnects`() = runTest {
        val fixture = testFixture(testDispatcher())
        fixture.adapter.connect(fixture.channel)
        clearMocks(fixture.audioManager, answers = false)

        fixture.events.emit(PlayerEvent.Stopped)
        advanceUntilIdle()

        verify(exactly = 1) { fixture.audioManager.setSendingHandler(null) }
        verify(exactly = 1) { fixture.audioManager.closeAudioConnection() }
    }

    @Test
    fun `Given an adapter When shutdown runs Then its event collector no longer reacts`() = runTest {
        val fixture = testFixture(testDispatcher())
        fixture.adapter.connect(fixture.channel)
        clearMocks(fixture.audioManager, answers = false)
        fixture.adapter.shutdown()

        fixture.events.emit(PlayerEvent.Stopped)
        advanceUntilIdle()

        verify(exactly = 1) { fixture.audioManager.closeAudioConnection() }
    }

    @Test
    fun `Given concurrent connect disconnect and close When settled Then no handler or stream is leaked`() {
        repeat(25) { iteration ->
            val fixture = Fixture()
            val gate = CountDownLatch(1)
            val threads = listOf(
                Thread({
                    gate.await()
                    runBlocking { fixture.adapter.connect(fixture.channel) }
                }, "connect-$iteration"),
                Thread({
                    gate.await()
                    runBlocking { fixture.adapter.disconnect() }
                }, "disconnect-$iteration"),
                Thread({
                    gate.await()
                    fixture.adapter.close()
                }, "close-$iteration"),
                Thread({
                    gate.await()
                    runBlocking { fixture.adapter.shutdown() }
                }, "shutdown-$iteration"),
            )
            threads.forEach { it.start() }
            gate.countDown()
            threads.forEach { it.join(5_000) }
            try {
                assertTrue(threads.none { it.isAlive }, "iteration $iteration: a race thread did not finish")
                assertNull(fixture.installedHandler, "iteration $iteration: a losing handler stayed installed")
                assertTrue(
                    fixture.streams.all { it.closeCalls > 0 },
                    "iteration $iteration: a created stream was leaked",
                )
            } finally {
                fixture.dispose()
            }
        }
    }

    @Test
    fun `Given a drained handler When play connects again Then install a fresh live pipeline`() = runTest {
        // Given: the first connection observed the natural end of playback, exhausting its handler.
        val fixture = testFixture(testDispatcher())
        fixture.adapter.connect(fixture.channel)
        val drained = requireNotNull(fixture.installedHandler) as JdaAudioSendHandler
        fixture.stream.nextState = PlayerAudioState.Ended
        assertFalse(drained.canProvide(), "an Ended stream must exhaust the old handler")

        val connected = fixture.adapter.connect(fixture.channel)

        assertTrue(connected)
        val fresh = requireNotNull(fixture.installedHandler) as JdaAudioSendHandler
        assertNotSame(drained, fresh, "a drained handler must never be reused")
        assertTrue(fresh.canProvide(), "the fresh handler must be able to send audio again")
        verify(exactly = 1) { fixture.audioManager.openAudioConnection(fixture.channel) }
        assertTrue(fixture.streams.size >= 2)
        assertTrue(fixture.streams.first().closeCalls > 0, "the drained stream must be released")
    }

    @Test
    fun `Given a voice session owned by another path When connect runs Then refuse without replacing it`() = runTest {
        // Given: JDA already reports a connected session this adapter never opened.
        val fixture = testFixture(testDispatcher(), initiallyConnected = true)

        val connected = fixture.adapter.connect(fixture.channel)

        assertFalse(connected)
        assertNull(fixture.installedHandler)
        assertTrue(fixture.streams.isEmpty(), "a foreign session must not create an audio pipeline")
        verify(exactly = 0) { fixture.audioManager.setSendingHandler(any()) }
        verify(exactly = 0) { fixture.audioManager.openAudioConnection(any()) }
        verify(exactly = 0) { fixture.audioManager.closeAudioConnection() }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun TestScope.testDispatcher(): CoroutineDispatcher = UnconfinedTestDispatcher(testScheduler)

    private fun testFixture(
        dispatcher: CoroutineDispatcher,
        initiallyConnected: Boolean = false,
    ): Fixture =
        Fixture(dispatcher, initiallyConnected).also { testFixtures += it }

    private class Fixture(
        dispatcher: CoroutineDispatcher? = null,
        initiallyConnected: Boolean = false,
    ) {
        val events = MutableSharedFlow<PlayerEvent>(extraBufferCapacity = 4)
        val stream = FakeStream()
        val streams = CopyOnWriteArrayList<FakeStream>()
        private var streamCalls = 0

        @Volatile
        private var connected = initiallyConnected

        @Volatile
        private var connectionStatus = if (initiallyConnected) {
            ConnectionStatus.CONNECTED
        } else {
            ConnectionStatus.NOT_CONNECTED
        }

        val engine: PlayerEngine = mockk()
        val audioManager: AudioManager = mockk()
        val guild: Guild = mockk()
        val channel: VoiceChannel = mockk {
            every { idLong } returns 42L
        }
        val scope = CoroutineScope(SupervisorJob() + (dispatcher ?: Dispatchers.Unconfined))
        var installedHandler: AudioSendHandler? = null
        private val handlerSlot = slot<AudioSendHandler>()
        val adapter: JdaGuildPlayerVoiceAdapter by lazy {
            if (dispatcher == null) {
                JdaGuildPlayerVoiceAdapter(guild, engine, scope)
            } else {
                JdaGuildPlayerVoiceAdapter(guild, engine, scope, dispatcher)
            }
        }

        init {
            every { engine.events } returns events
            every { engine.audioStream() } answers {
                val next = if (streamCalls++ == 0) stream else FakeStream()
                streams += next
                next
            }
            every { guild.audioManager } returns audioManager
            every { audioManager.setSendingHandler(capture(handlerSlot)) } answers {
                installedHandler = handlerSlot.captured
            }
            every { audioManager.setSendingHandler(null) } answers {
                installedHandler = null
            }
            every { audioManager.isConnected } answers { connected }
            every { audioManager.connectionStatus } answers { connectionStatus }
            every { audioManager.openAudioConnection(channel) } answers {
                connected = true
                connectionStatus = ConnectionStatus.CONNECTED
            }
            every { audioManager.closeAudioConnection() } answers {
                connected = false
                connectionStatus = ConnectionStatus.NOT_CONNECTED
            }
        }

        fun dispose() {
            scope.cancel()
        }
    }

    private class FakeStream : ContinuousPlayerAudioStream {
        var nextState: PlayerAudioState = PlayerAudioState.PausedSilence
        var closeCalls = 0
            private set

        override fun next(): PlayerAudioState = nextState

        override fun close() {
            closeCalls++
        }
    }
}
