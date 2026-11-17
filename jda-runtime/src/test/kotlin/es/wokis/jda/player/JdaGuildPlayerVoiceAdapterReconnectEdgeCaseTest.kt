package es.wokis.jda.player

import es.wokis.jda.voice.JdaAudioSendHandler
import io.mockk.coVerify
import io.mockk.every
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import net.dv8tion.jda.api.audio.hooks.ConnectionStatus
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
internal class JdaGuildPlayerVoiceAdapterReconnectEdgeCaseTest : PlayerVoiceAdapterTestSupport() {

    @Test
    fun `Given a CONNECTING session with isConnected false When reconnecting Then keep it and replace the pipeline`() = runTest {
        // Given: the first open is still handshaking, so JDA reports a status without isConnected.
        val fixture = voiceFixture()
        fixture.adapter.connect(fixture.channel)
        val oldHandler = requireNotNull(fixture.installedHandler) as JdaAudioSendHandler
        fixture.markConnectionStatus(ConnectionStatus.CONNECTING_AWAITING_ENDPOINT)
        fixture.order.clear()

        // When: playback connects to the same channel again.
        val connected = fixture.adapter.connect(fixture.channel)

        // Then: the same predicate treats CONNECTING as live, so no close/open is issued.
        assertTrue(connected)
        assertEquals(emptyList(), fixture.order, "a same-channel CONNECTING session must not close or reopen")
        assertFalse(oldHandler.canProvide(), "the exhausted handler must still be replaced")
        val fresh = requireNotNull(fixture.installedHandler) as JdaAudioSendHandler
        assertNotSame(oldHandler, fresh, "the pipeline must be fresh even without a reopen")
        assertTrue(fresh.canProvide())
        verify(exactly = 1) { fixture.audioManager.openAudioConnection(fixture.channel) }
        verify(exactly = 0) { fixture.audioManager.closeAudioConnection() }
    }

    @Test
    fun `Given a CONNECTING session this adapter never opened When reconnect runs Then classify it as foreign`() = runTest {
        // Given: JDA is handshaking a session that no adapter path owns.
        val fixture = voiceFixture()
        fixture.markConnectionStatus(ConnectionStatus.CONNECTING_AWAITING_ENDPOINT)

        // When: the adapter is asked to reconnect that session.
        val result = fixture.adapter.reconnect(fixture.channel)

        // Then: the same isConnectedOrConnecting predicate still detects the foreign session.
        assertEquals(JdaPlayerReconnectResult.ForeignSession, result)
        assertNull(fixture.installedHandler)
        assertTrue(fixture.streams.isEmpty(), "a foreign CONNECTING session must not create a pipeline")
        verify(exactly = 0) { fixture.audioManager.setSendingHandler(any()) }
        verify(exactly = 0) { fixture.audioManager.openAudioConnection(any()) }
        verify(exactly = 0) { fixture.audioManager.closeAudioConnection() }
    }

    @Test
    fun `Given a SHUTTING_DOWN session with isConnected false When reconnect targets B Then wait for NOT_CONNECTED`() = runTest {
        // Given: channel A is tearing down, so JDA reports SHUTTING_DOWN while isConnected is already false.
        val gate = CompletableDeferred<Unit>()
        val fixture = voiceFixture()
        fixture.deferDisconnect = true
        fixture.disconnectConfirmation = {
            fixture.order += "confirmed"
            gate.await()
            fixture.markDisconnected()
        }
        fixture.adapter.connect(fixture.channel)
        assertEquals(1, fixture.openCount(fixture.channel), "the setup must open channel A exactly once")
        fixture.markConnectionStatus(ConnectionStatus.SHUTTING_DOWN)
        fixture.order.clear()

        // When: the caller moves to channel B while A is still SHUTTING_DOWN.
        val reconnect = async { fixture.adapter.reconnect(fixture.otherChannel) }
        runCurrent()

        // Then: A counts as live, but neither channel is opened before NOT_CONNECTED.
        assertFalse(reconnect.isCompleted, "reconnect must wait for the disconnect confirmation")
        assertEquals(ConnectionStatus.SHUTTING_DOWN, fixture.currentConnectionStatus())
        assertNull(fixture.installedHandler, "the old pipeline must already be detached")
        assertEquals(listOf("close", "confirmed"), fixture.order)
        verify(exactly = 1) { fixture.audioManager.closeAudioConnection() }
        assertEquals(1, fixture.openCount(fixture.channel), "channel A must not be reopened while SHUTTING_DOWN")
        assertEquals(0, fixture.openCount(fixture.otherChannel), "channel B must not open before NOT_CONNECTED")

        // When: JDA finally reports NOT_CONNECTED, only the caller channel opens.
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(JdaPlayerReconnectResult.Reconnected, reconnect.await())
        assertEquals(listOf("close", "confirmed", "open"), fixture.order)
        assertNotNull(fixture.installedHandler, "the confirmed reconnect must install a fresh handler")
        assertEquals(1, fixture.openCount(fixture.channel), "the move to B must never reopen channel A")
        assertEquals(1, fixture.openCount(fixture.otherChannel), "channel B must open exactly once")
        verify(exactly = 1) { fixture.audioManager.openAudioConnection(fixture.otherChannel) }
    }

    @Test
    fun `Given a reconnect that timed out on A When the caller retries B Then reopen B instead of reporting foreign`() = runTest {
        // Given: channel A times out because JDA never confirms the close.
        var confirming = false
        val fixture = voiceFixture()
        fixture.deferDisconnect = true
        fixture.disconnectConfirmation = {
            fixture.order += "confirmed"
            if (confirming) {
                fixture.markDisconnected()
            } else {
                awaitCancellation()
            }
        }
        fixture.adapter.connect(fixture.channel)
        val oldHandler = requireNotNull(fixture.installedHandler) as JdaAudioSendHandler
        val oldStream = fixture.stream
        fixture.order.clear()

        val first = async { fixture.adapter.reconnect(fixture.channel) }
        advanceUntilIdle()

        assertEquals(JdaPlayerReconnectResult.TimedOut, first.await())
        assertEquals(1, fixture.streams.size, "a timed-out reconnect must not create a stream")
        assertEquals(1, fixture.openCount(fixture.channel), "only the setup may have opened A")
        assertEquals(0, fixture.openCount(fixture.otherChannel))

        // When: the caller retries the owned session but targets channel B.
        confirming = true
        fixture.order.clear()
        val retry = fixture.adapter.reconnect(fixture.otherChannel)

        // Then: ownership survives the timeout, so B is not foreign and opens only after confirmation.
        assertEquals(JdaPlayerReconnectResult.Reconnected, retry)
        assertNotEquals(JdaPlayerReconnectResult.ForeignSession, retry)
        assertNotEquals(JdaPlayerReconnectResult.TimedOut, retry)
        assertEquals(listOf("close", "confirmed", "open"), fixture.order)
        val fresh = requireNotNull(fixture.installedHandler) as JdaAudioSendHandler
        assertNotSame(oldHandler, fresh, "the retry must install a fresh handler")
        assertTrue(fresh.canProvide(), "the retried handler must be able to send audio")
        assertFalse(oldHandler.canProvide(), "the old handler must stay closed")
        assertTrue(oldStream.closeCalls > 0, "the old stream must be released")
        assertEquals(1, fixture.openCount(fixture.channel), "the retry must not reopen A")
        assertEquals(1, fixture.openCount(fixture.otherChannel), "the retry must open B exactly once")
        verify(exactly = 2) { fixture.audioManager.closeAudioConnection() }
        assertEquals(2, fixture.streams.size, "only the retry may create the second stream")
        assertTrue(fixture.streams.first().closeCalls > 0, "the timed-out stream must not leak")
        coVerify(exactly = 0) { fixture.engine.stop() }
        coVerify(exactly = 0) { fixture.engine.loadAndPlay(any(), any()) }
        verify(exactly = 0) { fixture.engine.close() }
    }

    @Test
    fun `Given no injected confirmation When reconnect runs Then poll JDA until NOT_CONNECTED before opening`() = runTest {
        // Given: the production confirmation polls isConnected; the first poll still sees the old connection.
        val fixture = voiceFixture()
        fixture.deferDisconnect = true
        var isConnectedReads = 0
        every { fixture.audioManager.isConnected } answers {
            isConnectedReads++
            when (isConnectedReads) {
                1 -> false
                2, 3, 4 -> true
                else -> {
                    fixture.markDisconnected()
                    false
                }
            }
        }
        fixture.adapter.connect(fixture.channel)
        val oldHandler = requireNotNull(fixture.installedHandler) as JdaAudioSendHandler

        // When: reconnect runs without a test seam, exercising the default awaitAudioDisconnect.
        val result = fixture.adapter.reconnect(fixture.channel)

        // Then: it polled at least once after the close request before reopening.
        assertEquals(JdaPlayerReconnectResult.Reconnected, result)
        assertTrue(isConnectedReads >= 5, "the default confirmation must poll JDA until NOT_CONNECTED")
        assertFalse(oldHandler.canProvide())
        val fresh = requireNotNull(fixture.installedHandler) as JdaAudioSendHandler
        assertTrue(fresh.canProvide(), "a confirmed poll must still install a live pipeline")
        assertEquals(2, fixture.openCount(fixture.channel), "the reconnect must reopen channel A once")
        verify(exactly = 1) { fixture.audioManager.closeAudioConnection() }
    }

    @Test
    fun `Given the begin close fails When reconnect runs Then clear the pending token and recover`() = runTest {
        // Given: the owned session is being detached, but its stream fails to close.
        val fixture = voiceFixture()
        fixture.adapter.connect(fixture.channel)
        fixture.stream.failOnClose = true

        assertFailsWith<IllegalStateException> { fixture.adapter.reconnect(fixture.channel) }

        // Then: the begin failure surfaced, the pipeline is detached and no token stays pending.
        assertNull(fixture.installedHandler)
        assertTrue(fixture.stream.closeCalls > 0, "the failing stream must still be closed")
        fixture.stream.failOnClose = false
        val retry = fixture.adapter.reconnect(fixture.channel)
        assertEquals(JdaPlayerReconnectResult.NotConnected, retry)
        assertNotEquals(JdaPlayerReconnectResult.InProgress, retry)
    }

    @Test
    fun `Given a reconnect already waiting When another reconnect starts Then report InProgress and keep the first`() = runTest {
        // Given: the first reconnect is parked on the disconnect confirmation.
        val gate = CompletableDeferred<Unit>()
        val fixture = voiceFixture()
        fixture.disconnectConfirmation = { gate.await() }
        fixture.adapter.connect(fixture.channel)
        fixture.order.clear()

        val first = async { fixture.adapter.reconnect(fixture.channel) }
        runCurrent()

        // When/Then: a second attempt is refused as InProgress without touching the connection.
        assertEquals(JdaPlayerReconnectResult.InProgress, fixture.adapter.reconnect(fixture.channel))
        assertEquals(listOf("close"), fixture.order, "the refused attempt must not close or open again")
        assertEquals(1, fixture.openCount(fixture.channel), "the refused attempt must not reopen")

        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(JdaPlayerReconnectResult.Reconnected, first.await())
        assertEquals(2, fixture.openCount(fixture.channel), "only the first attempt may reopen")
    }

    @Test
    fun `Given an owned session JDA already dropped When reconnect runs Then clear stale ownership`() = runTest {
        // Given: the adapter believes it owns a session that JDA no longer reports.
        val fixture = voiceFixture()
        fixture.adapter.connect(fixture.channel)
        fixture.markDisconnected()

        // When/Then: reconnect reports NotConnected and releases the stale ownership.
        assertEquals(JdaPlayerReconnectResult.NotConnected, fixture.adapter.reconnect(fixture.otherChannel))
        verify(exactly = 0) { fixture.audioManager.closeAudioConnection() }

        // And: a later foreign live session is no longer masked as ours.
        fixture.markConnected()
        assertEquals(JdaPlayerReconnectResult.ForeignSession, fixture.adapter.reconnect(fixture.otherChannel))
        assertEquals(1, fixture.openCount(fixture.channel), "a refused reconnect must not reopen")
        assertEquals(0, fixture.openCount(fixture.otherChannel))
    }

    @Test
    fun `Given a reconnect waiting for confirmation When the caller is cancelled Then clear the pending state`() = runTest {
        // Given: the reconnect is parked on the confirmation.
        val gate = CompletableDeferred<Unit>()
        val fixture = voiceFixture()
        fixture.deferDisconnect = true
        fixture.disconnectConfirmation = {
            gate.await()
            fixture.markDisconnected()
        }
        fixture.adapter.connect(fixture.channel)

        val caller = launch { fixture.adapter.reconnect(fixture.channel) }
        runCurrent()
        assertFalse(caller.isCompleted)

        // When: the caller is cancelled during the wait.
        caller.cancelAndJoin()

        // Then: the pending token was released, so a later attempt is not InProgress and can reopen.
        gate.complete(Unit)
        val retry = fixture.adapter.reconnect(fixture.channel)
        assertEquals(JdaPlayerReconnectResult.Reconnected, retry)
        assertNotEquals(JdaPlayerReconnectResult.InProgress, retry)
    }
}
