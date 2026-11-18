package es.wokis.jda.player

import es.wokis.jda.voice.JdaAudioSendHandler
import io.mockk.coVerify
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
internal class JdaGuildPlayerVoiceAdapterShutdownTest : PlayerVoiceAdapterTestSupport() {

    @Test
    fun `Given a pending disconnect confirmation When shutdown is requested Then abort the reopen and clean up`() = runTest {
        // Given: a reconnect that already closed the pipeline and is waiting for JDA to confirm.
        val gate = CompletableDeferred<Unit>()
        val fixture = voiceFixture()
        fixture.disconnectConfirmation = { gate.await() }
        fixture.adapter.connect(fixture.channel)
        val oldHandler = requireNotNull(fixture.installedHandler) as JdaAudioSendHandler
        val oldStream = fixture.stream
        val opensBeforeShutdown = fixture.openCount(fixture.channel)

        val reconnect = async { fixture.adapter.reconnect(fixture.channel) }
        runCurrent()
        assertFalse(reconnect.isCompleted, "reconnect must be waiting for the disconnect confirmation")

        // When: shutdown starts while the confirmation is still pending.
        val shutdown = async { fixture.adapter.shutdown() }
        runCurrent()
        gate.complete(Unit)
        advanceUntilIdle()

        // Then: shutdown completes and the stale reopen is aborted without leaking the pipeline.
        shutdown.await()
        assertEquals(JdaPlayerReconnectResult.NotConnected, reconnect.await())
        assertNull(fixture.installedHandler, "a shutdown during reconnect must not leave a stale handler")
        assertFalse(oldHandler.canProvide(), "the old handler must stay closed")
        assertTrue(oldStream.closeCalls > 0, "the old stream must be released")
        assertEquals(opensBeforeShutdown, fixture.openCount(fixture.channel), "shutdown must not reopen")
        verify(exactly = 1) { fixture.audioManager.setSendingHandler(null) }
        assertEquals(1, fixture.streams.size, "a shut down reconnect must not create a stream")
        assertTrue(fixture.streams.all { it.closeCalls > 0 }, "a shutdown during reconnect must not leak a stream")
    }

    @Test
    fun `Given a pending reconnect on the serial dispatcher When shutdown runs Then finish within its bound and block reopens`() = runTest {
        // Given: a reconnect parked on the confirmation on a queued serial dispatcher.
        val gate = CompletableDeferred<Unit>()
        val fixture = voiceFixture(serialDispatcherOverride = StandardTestDispatcher(testScheduler))
        fixture.disconnectConfirmation = { gate.await() }
        fixture.adapter.connect(fixture.channel)
        assertNotNull(fixture.installedHandler)
        val opensBeforeShutdown = fixture.openCount(fixture.channel)

        val reconnect = async { fixture.adapter.reconnect(fixture.channel) }
        runCurrent()
        assertFalse(reconnect.isCompleted, "reconnect must be waiting for the disconnect confirmation")
        val shutdownStartedAt = testScheduler.currentTime

        // When: shutdown starts while the reconnect is still pending.
        val shutdown = async { fixture.adapter.shutdown() }
        advanceUntilIdle()

        // Then: shutdown did not wait for the pending confirmation and stayed within its bound.
        assertTrue(shutdown.isCompleted, "shutdown must not wait for the reconnect confirmation")
        assertFalse(gate.isCompleted, "the test never confirmed the disconnect")
        assertTrue(
            testScheduler.currentTime - shutdownStartedAt <= CLOSE_TIMEOUT_MILLIS,
            "shutdown must complete within the close timeout bound",
        )
        assertEquals(JdaPlayerReconnectResult.NotConnected, reconnect.await())
        assertNull(fixture.installedHandler, "shutdown must not leave a stale handler")
        verify(exactly = 1) { fixture.audioManager.setSendingHandler(null) }
        assertEquals(1, fixture.streams.size, "an aborted reconnect must not create a stream")
        assertTrue(fixture.streams.all { it.closeCalls > 0 }, "shutdown must not leak a stream")

        // And: no later reopen is allowed after shutdown.
        val laterReopen = fixture.adapter.reconnect(fixture.channel)
        assertEquals(JdaPlayerReconnectResult.NotConnected, laterReopen)
        assertEquals(opensBeforeShutdown, fixture.openCount(fixture.channel), "shutdown must block every reopen")
    }

    @Test
    fun `Given a pending reconnect When a racing connect and shutdown interleave Then no pipeline leaks`() = runTest {
        // Given: a reconnect is parked on the confirmation and a same-channel connect is queued after it.
        val gate = CompletableDeferred<Unit>()
        val fixture = voiceFixture(serialDispatcherOverride = StandardTestDispatcher(testScheduler))
        fixture.disconnectConfirmation = {
            fixture.order += "confirmed"
            gate.await()
        }
        fixture.adapter.connect(fixture.channel)
        fixture.order.clear()
        val opensBeforeShutdown = fixture.openCount(fixture.channel)

        val reconnect = async { fixture.adapter.reconnect(fixture.channel) }
        runCurrent()
        assertFalse(reconnect.isCompleted)

        val racingConnect = async { fixture.adapter.connect(fixture.channel) }
        runCurrent()
        val shutdownStartIndex = fixture.order.size
        val shutdown = async { fixture.adapter.shutdown() }
        runCurrent()
        gate.complete(Unit)
        advanceUntilIdle()

        // Then: the race settles without a leaked pipeline and shutdown leaves nothing installed.
        assertFalse(racingConnect.await(), "connect must not steal a session a reconnect is closing")
        shutdown.await()
        assertNotEquals(JdaPlayerReconnectResult.Reconnected, reconnect.await())
        assertNull(fixture.installedHandler, "shutdown must detach any pipeline the racing connect installed")
        assertTrue(
            fixture.streams.all { it.closeCalls > 0 },
            "every stream created during the race must be released",
        )
        assertFalse(
            fixture.order.drop(shutdownStartIndex).contains("open"),
            "no channel may reopen after shutdown starts: ${fixture.order}",
        )
        assertEquals(opensBeforeShutdown, fixture.openCount(fixture.channel), "shutdown must block every reopen")
        coVerify(exactly = 0) { fixture.engine.stop() }
        coVerify(exactly = 0) { fixture.engine.loadAndPlay(any(), any()) }
    }

    private companion object {
        /** Duplicated by hand: mirrors the private production `CLOSE_TIMEOUT_MILLIS` bound. */
        private const val CLOSE_TIMEOUT_MILLIS = 5_000L
    }
}
