package es.wokis.jda.player

import es.wokis.jda.voice.JdaAudioSendHandler
import io.mockk.clearMocks
import io.mockk.coVerify
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Regression coverage for a failing disconnect confirmation. The attempt token must always be
 * released, no pipeline may be installed for an unconfirmed close, and a later retry must be
 * possible; cancellation keeps propagating as cancellation.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class JdaGuildPlayerVoiceAdapterReconnectConfirmationFailureTest : PlayerVoiceAdapterTestSupport() {

    @Test
    fun `Given a failing disconnect confirmation When reconnect runs Then fail safely and release the attempt`() = runTest {
        // Given: an owned live session whose confirmation throws before JDA reports the disconnect.
        val fixture = voiceFixture()
        fixture.deferDisconnect = true
        var confirmations = 0
        fixture.disconnectConfirmation = {
            confirmations++
            if (confirmations == 1) error("confirmation failed")
            fixture.order += "confirmed"
            fixture.markDisconnected()
        }
        fixture.adapter.connect(fixture.channel)
        val oldHandler = requireNotNull(fixture.installedHandler) as JdaAudioSendHandler
        val oldStream = fixture.stream
        fixture.order.clear()
        clearMocks(fixture.audioManager, answers = false)

        // When: the confirmation fails after the old pipeline and connection were already detached.
        val failure = assertFailsWith<IllegalStateException> {
            fixture.adapter.reconnect(fixture.channel)
        }

        // Then: the failure surfaces instead of a false success and no fresh pipeline is installed.
        assertEquals("confirmation failed", failure.message)
        assertEquals(listOf("close"), fixture.order)
        assertNull(fixture.installedHandler, "a failed confirmation must not install a handler")
        assertFalse(oldHandler.canProvide(), "the old handler must stay closed")
        assertTrue(oldStream.closeCalls > 0, "the old stream must be released")
        assertEquals(1, fixture.streams.size, "a failed confirmation must not create a stream")
        verify(exactly = 1) { fixture.engine.audioStream() }
        verify(exactly = 1) { fixture.audioManager.closeAudioConnection() }
        verify(exactly = 1) { fixture.audioManager.setSendingHandler(null) }
        verify(exactly = 0) { fixture.audioManager.openAudioConnection(any()) }
        coVerify(exactly = 0) { fixture.engine.stop() }
        coVerify(exactly = 0) { fixture.engine.loadAndPlay(any(), any()) }

        // When: JDA confirms the disconnect and the caller retries the same channel.
        fixture.order.clear()
        val retry = fixture.adapter.reconnect(fixture.channel)

        // Then: the released token lets the retry reopen instead of remaining permanently InProgress.
        assertEquals(JdaPlayerReconnectResult.Reconnected, retry)
        assertNotEquals(JdaPlayerReconnectResult.InProgress, retry)
        assertEquals(listOf("close", "confirmed", "open"), fixture.order)
        val fresh = requireNotNull(fixture.installedHandler) as JdaAudioSendHandler
        assertNotSame(oldHandler, fresh, "the retry must install a fresh handler")
        assertTrue(fresh.canProvide(), "the retried handler must be able to send audio")
        assertFalse(oldHandler.canProvide(), "the old handler must stay closed")
        assertEquals(2, fixture.streams.size, "only the retry may create the second stream")
        assertTrue(fixture.streams.first().closeCalls > 0, "the failed attempt's stream must not leak")
        assertEquals(0, fixture.streams.last().closeCalls, "the retried stream must stay live")
        verify(exactly = 2) { fixture.engine.audioStream() }
        verify(exactly = 2) { fixture.audioManager.closeAudioConnection() }
        verify(exactly = 1) { fixture.audioManager.openAudioConnection(fixture.channel) }
    }

    @Test
    fun `Given a cancelled disconnect confirmation When reconnect runs Then propagate and allow a retry`() = runTest {
        // Given: an owned live session whose confirmation cancels instead of confirming.
        val fixture = voiceFixture()
        fixture.deferDisconnect = true
        var confirmations = 0
        fixture.disconnectConfirmation = {
            confirmations++
            if (confirmations == 1) throw CancellationException("confirmation cancelled")
            fixture.order += "confirmed"
            fixture.markDisconnected()
        }
        fixture.adapter.connect(fixture.channel)
        val oldHandler = requireNotNull(fixture.installedHandler) as JdaAudioSendHandler
        val oldStream = fixture.stream
        fixture.order.clear()
        clearMocks(fixture.audioManager, answers = false)

        // When: the confirmation throws a cancellation while the caller itself is still active.
        val failure = assertFailsWith<CancellationException> {
            fixture.adapter.reconnect(fixture.channel)
        }

        // Then: cancellation still propagates and the unconfirmed close installs nothing.
        assertEquals("confirmation cancelled", failure.message)
        assertEquals(listOf("close"), fixture.order)
        assertNull(fixture.installedHandler, "a cancelled confirmation must not install a handler")
        assertFalse(oldHandler.canProvide(), "the old handler must stay closed")
        assertTrue(oldStream.closeCalls > 0, "the old stream must be released")
        assertEquals(1, fixture.streams.size, "a cancelled confirmation must not create a stream")
        verify(exactly = 1) { fixture.engine.audioStream() }
        verify(exactly = 1) { fixture.audioManager.closeAudioConnection() }
        verify(exactly = 0) { fixture.audioManager.openAudioConnection(any()) }

        // When: the disconnect is confirmed later and the caller retries.
        fixture.order.clear()
        val retry = fixture.adapter.reconnect(fixture.channel)

        // Then: the cancellation also cleared the attempt token, so the retry can reopen.
        assertEquals(JdaPlayerReconnectResult.Reconnected, retry)
        assertNotEquals(JdaPlayerReconnectResult.InProgress, retry)
        assertEquals(listOf("close", "confirmed", "open"), fixture.order)
        val fresh = requireNotNull(fixture.installedHandler) as JdaAudioSendHandler
        assertNotSame(oldHandler, fresh, "the retry must install a fresh handler")
        assertTrue(fresh.canProvide(), "the retried handler must be able to send audio")
        assertEquals(2, fixture.streams.size, "only the retry may create the second stream")
        assertTrue(fixture.streams.first().closeCalls > 0, "the cancelled attempt's stream must not leak")
        assertEquals(0, fixture.streams.last().closeCalls, "the retried stream must stay live")
        verify(exactly = 2) { fixture.engine.audioStream() }
        verify(exactly = 2) { fixture.audioManager.closeAudioConnection() }
        verify(exactly = 1) { fixture.audioManager.openAudioConnection(fixture.channel) }
    }
}
