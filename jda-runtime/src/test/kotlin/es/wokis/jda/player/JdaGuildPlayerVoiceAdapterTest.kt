package es.wokis.jda.player

import es.wokis.jda.voice.JdaAudioSendHandler
import es.wokis.player.engine.audio.PlayerAudioState
import es.wokis.player.engine.event.PlayerEvent
import io.mockk.clearMocks
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
internal class JdaGuildPlayerVoiceAdapterTest : PlayerVoiceAdapterTestSupport() {

    @Test
    fun `Given a guild voice channel When connect runs Then the existing send handler is installed`() = runTest {
        val fixture = voiceFixture()

        fixture.adapter.connect(fixture.channel)

        assertNotNull(fixture.installedHandler)
        assertTrue(fixture.installedHandler is JdaAudioSendHandler)
        verify(exactly = 1) { fixture.audioManager.openAudioConnection(fixture.channel) }
    }

    @Test
    fun `Given a connected adapter When disconnect runs Then handler and stream are closed`() = runTest {
        val fixture = voiceFixture()
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
        val fixture = voiceFixture()
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
        val fixture = voiceFixture()
        fixture.adapter.connect(fixture.channel)
        clearMocks(fixture.audioManager, answers = false)

        fixture.events.emit(PlayerEvent.Stopped)
        advanceUntilIdle()

        verify(exactly = 1) { fixture.audioManager.setSendingHandler(null) }
        verify(exactly = 1) { fixture.audioManager.closeAudioConnection() }
    }

    @Test
    fun `Given an adapter When shutdown runs Then its event collector no longer reacts`() = runTest {
        val fixture = voiceFixture()
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
            val fixture = realThreadVoiceFixture()
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
        val fixture = voiceFixture()
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
}
