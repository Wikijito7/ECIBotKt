package es.wokis.jda.player

import es.wokis.jda.voice.JdaAudioSendHandler
import io.mockk.clearMocks
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
internal class JdaGuildPlayerVoiceAdapterReconnectTest : PlayerVoiceAdapterTestSupport() {

    @Test
    fun `Given an owned live session When reconnect runs Then close wait for confirmation and reopen on the caller channel`() = runTest {
        // Given: an adapter that owns an active session on the caller's channel.
        val fixture = voiceFixture()
        fixture.disconnectConfirmation = { fixture.order += "confirmed" }
        fixture.adapter.connect(fixture.channel)
        val oldHandler = requireNotNull(fixture.installedHandler) as JdaAudioSendHandler
        val oldStream = fixture.stream
        fixture.order.clear()
        clearMocks(fixture.audioManager, answers = false)

        val result = fixture.adapter.reconnect(fixture.channel)

        // Then: close, then the confirmed disconnect, and only then the fresh open.
        assertEquals(JdaPlayerReconnectResult.Reconnected, result)
        assertEquals(listOf("close", "confirmed", "open"), fixture.order)
        assertFalse(oldHandler.canProvide(), "the old handler must be closed before reopening")
        assertTrue(oldStream.closeCalls > 0, "the old stream must be released")
        val fresh = requireNotNull(fixture.installedHandler) as JdaAudioSendHandler
        assertNotSame(oldHandler, fresh, "reconnect must install a fresh pipeline")
        assertTrue(fresh.canProvide(), "the fresh handler must be able to send audio")
        verifyOrder {
            fixture.audioManager.closeAudioConnection()
            fixture.audioManager.openAudioConnection(fixture.channel)
        }
        verify(exactly = 1) { fixture.audioManager.closeAudioConnection() }
        verify(exactly = 1) { fixture.audioManager.openAudioConnection(fixture.channel) }
        assertTrue(fixture.streams.size >= 2, "reconnect must create a new audio stream")
    }

    @Test
    fun `Given a live session on channel A When reconnect targets channel B Then close A await it and open B fresh`() = runTest {
        // Given: an owned live session on channel A.
        val fixture = voiceFixture()
        fixture.disconnectConfirmation = { fixture.order += "confirmed" }
        fixture.adapter.connect(fixture.channel)
        val oldHandler = requireNotNull(fixture.installedHandler) as JdaAudioSendHandler
        val oldStream = fixture.stream
        fixture.order.clear()
        clearMocks(fixture.audioManager, answers = false)

        val result = fixture.adapter.reconnect(fixture.otherChannel)

        // Then: A is closed and confirmed before B is opened with a fresh pipeline.
        assertEquals(JdaPlayerReconnectResult.Reconnected, result)
        assertEquals(listOf("close", "confirmed", "open"), fixture.order)
        assertFalse(oldHandler.canProvide(), "the channel A handler must be closed before opening channel B")
        assertTrue(oldStream.closeCalls > 0, "the channel A stream must be released")
        val fresh = requireNotNull(fixture.installedHandler) as JdaAudioSendHandler
        assertNotSame(oldHandler, fresh, "reconnect must install a fresh handler")
        assertTrue(fresh.canProvide(), "the fresh handler must be able to send audio")
        verifyOrder {
            fixture.audioManager.closeAudioConnection()
            fixture.audioManager.openAudioConnection(fixture.otherChannel)
        }
        verify(exactly = 1) { fixture.audioManager.closeAudioConnection() }
        verify(exactly = 1) { fixture.audioManager.openAudioConnection(fixture.otherChannel) }
        verify(exactly = 0) { fixture.audioManager.openAudioConnection(fixture.channel) }
        assertTrue(fixture.streams.size >= 2, "reconnect to channel B must create a new audio stream")
    }

    @Test
    fun `Given a pending async close When reconnect waits Then never open before confirmation`() = runTest {
        // Given: the JDA close has been requested but the disconnect is not yet observed.
        val gate = CompletableDeferred<Unit>()
        val fixture = voiceFixture()
        fixture.disconnectConfirmation = { gate.await() }
        fixture.adapter.connect(fixture.channel)
        clearMocks(fixture.audioManager, answers = false)

        val reconnect = async { fixture.adapter.reconnect(fixture.channel) }
        runCurrent()

        // Then: the pipeline is closed but no fresh open happens while the close is pending.
        assertFalse(reconnect.isCompleted, "reconnect must wait for the disconnect confirmation")
        assertNull(fixture.installedHandler, "the old handler must already be detached")
        verify(exactly = 0) { fixture.audioManager.openAudioConnection(any()) }
        verify(exactly = 1) { fixture.audioManager.closeAudioConnection() }

        // When: JDA confirms the disconnect, the same reconnect completes and reopens.
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(JdaPlayerReconnectResult.Reconnected, reconnect.await())
        verify(exactly = 1) { fixture.audioManager.openAudioConnection(fixture.channel) }
        assertNotNull(fixture.installedHandler, "a confirmed reconnect must install a fresh handler")
        assertTrue(fixture.streams.size >= 2, "a confirmed reconnect must create a new audio stream")
    }

    @Test
    fun `Given a pending disconnect confirmation When close is requested Then abort the reopen and clean up`() = runTest {
        // Given: a reconnect that already closed the pipeline and is waiting for JDA to confirm.
        val gate = CompletableDeferred<Unit>()
        val fixture = voiceFixture()
        fixture.disconnectConfirmation = { gate.await() }
        fixture.adapter.connect(fixture.channel)
        val oldHandler = requireNotNull(fixture.installedHandler) as JdaAudioSendHandler
        val oldStream = fixture.stream
        clearMocks(fixture.audioManager, answers = false)

        val reconnect = async { fixture.adapter.reconnect(fixture.channel) }
        runCurrent()
        assertFalse(reconnect.isCompleted, "reconnect must be waiting for the disconnect confirmation")
        assertNull(fixture.installedHandler, "the old handler must already be detached")
        verify(exactly = 1) { fixture.audioManager.closeAudioConnection() }
        verify(exactly = 0) { fixture.audioManager.openAudioConnection(any()) }

        // When: a close is requested while the confirmation is still pending.
        fixture.adapter.close()
        gate.complete(Unit)
        advanceUntilIdle()

        // Then: the stale reopen is aborted and nothing is left installed.
        assertEquals(JdaPlayerReconnectResult.NotConnected, reconnect.await())
        assertNull(fixture.installedHandler, "a close during reconnect must not leave a stale handler")
        assertFalse(oldHandler.canProvide(), "the old handler must stay closed")
        assertTrue(oldStream.closeCalls > 0, "the old stream must be released")
        verify(exactly = 0) { fixture.audioManager.openAudioConnection(any()) }
        verify(exactly = 1) { fixture.audioManager.setSendingHandler(null) }
        assertEquals(1, fixture.streams.size, "a closed reconnect must not create a stream")
        assertTrue(fixture.streams.all { it.closeCalls > 0 }, "a close during reconnect must not leak a stream")
    }

    @Test
    fun `Given a disconnect that is never confirmed When reconnect times out Then fail safely without opening`() = runTest {
        // Given: close is requested but the disconnect confirmation never arrives.
        val fixture = voiceFixture()
        fixture.disconnectConfirmation = { awaitCancellation() }
        fixture.adapter.connect(fixture.channel)
        val oldHandler = requireNotNull(fixture.installedHandler) as JdaAudioSendHandler
        clearMocks(fixture.audioManager, answers = false)

        val reconnect = async { fixture.adapter.reconnect(fixture.channel) }
        advanceUntilIdle()

        // Then: a safe failure, no false success, no open and no leaked handler or stream.
        val result = reconnect.await()
        assertEquals(JdaPlayerReconnectResult.TimedOut, result)
        assertNotEquals(JdaPlayerReconnectResult.Reconnected, result)
        assertNull(fixture.installedHandler, "a timed-out reconnect must not install a handler")
        assertFalse(oldHandler.canProvide(), "the old handler must stay closed")
        verify(exactly = 0) { fixture.audioManager.openAudioConnection(any()) }
        verify(exactly = 1) { fixture.audioManager.closeAudioConnection() }
        assertTrue(fixture.streams.all { it.closeCalls > 0 }, "no stream may leak on timeout")
    }

    @Test
    fun `Given a timed out reconnect When confirmation begins and the same channel retries Then reopen`() = runTest {
        // Given: the close is requested while JDA still reports the connection and never confirms in time.
        var confirming = false
        val fixture = voiceFixture()
        fixture.deferDisconnect = true
        fixture.disconnectConfirmation = {
            if (confirming) {
                fixture.markDisconnected()
            } else {
                awaitCancellation()
            }
        }
        fixture.adapter.connect(fixture.channel)
        val oldHandler = requireNotNull(fixture.installedHandler) as JdaAudioSendHandler
        val oldStream = fixture.stream
        clearMocks(fixture.audioManager, answers = false)

        val first = async { fixture.adapter.reconnect(fixture.channel) }
        advanceUntilIdle()

        assertEquals(JdaPlayerReconnectResult.TimedOut, first.await())
        assertEquals(1, fixture.streams.size, "a timed-out reconnect must not create a stream")
        verify(exactly = 0) { fixture.audioManager.openAudioConnection(any()) }

        // When: JDA begins confirming the disconnect and the caller retries the same channel.
        confirming = true
        val retry = fixture.adapter.reconnect(fixture.channel)

        // Then: the retained ownership keeps the session ours instead of looking foreign.
        assertEquals(JdaPlayerReconnectResult.Reconnected, retry)
        assertNotEquals(JdaPlayerReconnectResult.ForeignSession, retry)
        assertNotEquals(JdaPlayerReconnectResult.TimedOut, retry)
        val fresh = requireNotNull(fixture.installedHandler) as JdaAudioSendHandler
        assertNotSame(oldHandler, fresh, "the retry must install a fresh handler")
        assertTrue(fresh.canProvide(), "the retried handler must be able to send audio")
        assertFalse(oldHandler.canProvide(), "the old handler must stay closed")
        assertTrue(oldStream.closeCalls > 0, "the old stream must be released")
        verify(exactly = 1) { fixture.audioManager.openAudioConnection(fixture.channel) }
        verify(exactly = 2) { fixture.audioManager.closeAudioConnection() }
        assertTrue(fixture.streams.size >= 2, "the retry must create a new audio stream")
    }

    @Test
    fun `Given an already disconnected adapter When reconnect runs Then refuse instead of silently connecting`() = runTest {
        val fixture = voiceFixture()

        val result = fixture.adapter.reconnect(fixture.channel)

        assertEquals(JdaPlayerReconnectResult.NotConnected, result)
        assertNull(fixture.installedHandler)
        assertTrue(fixture.streams.isEmpty(), "a refused reconnect must not create an audio pipeline")
        verify(exactly = 0) { fixture.audioManager.setSendingHandler(any()) }
        verify(exactly = 0) { fixture.audioManager.openAudioConnection(any()) }
        verify(exactly = 0) { fixture.audioManager.closeAudioConnection() }
    }

    @Test
    fun `Given a foreign voice session When reconnect runs Then refuse without closing or opening it`() = runTest {
        // Given: JDA reports a connected session this adapter never opened.
        val fixture = voiceFixture(initiallyConnected = true)

        val result = fixture.adapter.reconnect(fixture.channel)

        assertEquals(JdaPlayerReconnectResult.ForeignSession, result)
        assertNull(fixture.installedHandler)
        assertTrue(fixture.streams.isEmpty(), "a foreign session must not create an audio pipeline")
        verify(exactly = 0) { fixture.audioManager.setSendingHandler(any()) }
        verify(exactly = 0) { fixture.audioManager.openAudioConnection(any()) }
        verify(exactly = 0) { fixture.audioManager.closeAudioConnection() }
    }

    @Test
    fun `Given a voice session owned by another path When connect runs Then refuse without replacing it`() = runTest {
        // Given: JDA already reports a connected session this adapter never opened.
        val fixture = voiceFixture(initiallyConnected = true)

        val connected = fixture.adapter.connect(fixture.channel)

        assertFalse(connected)
        assertNull(fixture.installedHandler)
        assertTrue(fixture.streams.isEmpty(), "a foreign session must not create an audio pipeline")
        verify(exactly = 0) { fixture.audioManager.setSendingHandler(any()) }
        verify(exactly = 0) { fixture.audioManager.openAudioConnection(any()) }
        verify(exactly = 0) { fixture.audioManager.closeAudioConnection() }
    }
}
