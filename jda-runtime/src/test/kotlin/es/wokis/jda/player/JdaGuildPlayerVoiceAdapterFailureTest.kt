package es.wokis.jda.player

import es.wokis.jda.voice.JdaAudioSendHandler
import io.mockk.every
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
internal class JdaGuildPlayerVoiceAdapterFailureTest : PlayerVoiceAdapterTestSupport() {

    @Test
    fun `Given a stream that fails to close When disconnect runs Then the remaining cleanup still runs`() = runTest {
        val fixture = voiceFixture()
        fixture.adapter.connect(fixture.channel)
        val handler = requireNotNull(fixture.installedHandler) as JdaAudioSendHandler
        fixture.stream.failOnClose = true

        assertFailsWith<IllegalStateException>("the stream close failure must surface") {
            fixture.adapter.disconnect()
        }

        verify(exactly = 1) { fixture.audioManager.setSendingHandler(null) }
        verify(exactly = 1) { fixture.audioManager.closeAudioConnection() }
        assertFalse(handler.canProvide(), "the handler must be closed even when the stream close fails")
        assertTrue(fixture.stream.closeCalls > 0)
    }

    @Test
    fun `Given detaching the send handler fails When disconnect runs Then JDA close and stream release still run`() = runTest {
        val fixture = voiceFixture()
        fixture.adapter.connect(fixture.channel)
        val handler = requireNotNull(fixture.installedHandler) as JdaAudioSendHandler
        every { fixture.audioManager.setSendingHandler(null) } throws IllegalStateException("detach failed")

        assertFailsWith<IllegalStateException> { fixture.adapter.disconnect() }

        verify(exactly = 1) { fixture.audioManager.setSendingHandler(null) }
        verify(exactly = 1) { fixture.audioManager.closeAudioConnection() }
        assertFalse(handler.canProvide(), "the handler must be closed before the detach attempt")
        assertTrue(fixture.stream.closeCalls > 0, "the stream must be released after a detach failure")

        // And: the adapter already dropped its references, so a retry cannot repeat the cleanup.
        fixture.adapter.disconnect()
        verify(exactly = 1) { fixture.audioManager.setSendingHandler(null) }
        verify(exactly = 1) { fixture.audioManager.closeAudioConnection() }
    }

    @Test
    fun `Given closing the JDA connection fails When disconnect runs Then handler and stream are still released`() = runTest {
        val fixture = voiceFixture()
        fixture.adapter.connect(fixture.channel)
        val handler = requireNotNull(fixture.installedHandler) as JdaAudioSendHandler
        every { fixture.audioManager.closeAudioConnection() } throws IllegalStateException("close failed")

        assertFailsWith<IllegalStateException> { fixture.adapter.disconnect() }

        verify(exactly = 1) { fixture.audioManager.setSendingHandler(null) }
        verify(exactly = 1) { fixture.audioManager.closeAudioConnection() }
        assertFalse(handler.canProvide(), "the handler must be closed even when JDA close fails")
        assertTrue(fixture.stream.closeCalls > 0, "the stream must be released when JDA close fails")
    }

    @Test
    fun `Given two cleanup failures When disconnect runs Then the first is primary and the second is suppressed`() = runTest {
        // Given: the detach and the JDA close both fail while the stream is still installed.
        val fixture = voiceFixture()
        fixture.adapter.connect(fixture.channel)
        val handler = requireNotNull(fixture.installedHandler) as JdaAudioSendHandler
        val detachFailure = IllegalStateException("detach failed")
        val closeFailure = IllegalStateException("close failed")
        every { fixture.audioManager.setSendingHandler(null) } throws detachFailure
        every { fixture.audioManager.closeAudioConnection() } throws closeFailure

        // When: the cleanup runs to completion.
        val thrown = assertFailsWith<IllegalStateException> { fixture.adapter.disconnect() }

        // Then: the first failure wins and the second is attached instead of swallowed.
        assertSame(detachFailure, thrown, "the first cleanup failure must stay the primary")
        assertEquals(listOf(closeFailure), thrown.suppressed.toList())
        verify(exactly = 1) { fixture.audioManager.setSendingHandler(null) }
        verify(exactly = 1) { fixture.audioManager.closeAudioConnection() }
        assertFalse(handler.canProvide(), "the handler must be closed before both cleanup failures")

        // And: the remaining cleanup action still ran after the primary and its suppression.
        assertTrue(fixture.stream.closeCalls > 0, "the stream release must run despite two earlier failures")
    }

    @Test
    fun `Given the connect open fails When connect runs Then the pipeline is released and the failure surfaces`() = runTest {
        val fixture = voiceFixture()
        every { fixture.audioManager.openAudioConnection(fixture.channel) } throws IllegalStateException("open failed")

        assertFailsWith<IllegalStateException> { fixture.adapter.connect(fixture.channel) }

        assertNull(fixture.installedHandler, "a failed connect must not leave a handler installed")
        assertEquals(1, fixture.streams.size)
        assertTrue(fixture.streams.single().closeCalls > 0, "a failed connect must release its stream")
        verify(exactly = 1) { fixture.audioManager.setSendingHandler(null) }
        verify(exactly = 1) { fixture.audioManager.openAudioConnection(fixture.channel) }
        verify(exactly = 0) { fixture.audioManager.closeAudioConnection() }
    }

    @Test
    fun `Given the reopen open fails When reconnect runs Then the fresh pipeline is released and the failure surfaces`() = runTest {
        val fixture = voiceFixture()
        fixture.disconnectConfirmation = { fixture.order += "confirmed" }
        fixture.adapter.connect(fixture.channel)
        val oldHandler = requireNotNull(fixture.installedHandler) as JdaAudioSendHandler
        val oldStream = fixture.stream
        every { fixture.audioManager.openAudioConnection(fixture.channel) } throws IllegalStateException("open failed")

        assertFailsWith<IllegalStateException> { fixture.adapter.reconnect(fixture.channel) }

        assertNull(fixture.installedHandler, "a failed reopen must not leave a handler installed")
        assertFalse(oldHandler.canProvide(), "the old handler must stay closed")
        assertTrue(oldStream.closeCalls > 0, "the old stream must be released")
        assertEquals(2, fixture.streams.size)
        assertTrue(fixture.streams.all { it.closeCalls > 0 }, "a failed reopen must release the fresh stream too")
        verify(exactly = 2) { fixture.audioManager.openAudioConnection(fixture.channel) }
        verify(exactly = 2) { fixture.audioManager.setSendingHandler(null) }
        verify(exactly = 1) { fixture.audioManager.closeAudioConnection() }
    }

    @Test
    fun `Given a queued close wins during reopen When the caller channel opens Then abort with NotConnected`() = runTest {
        // Given: a close request lands while JDA is opening the caller channel.
        val fixture = voiceFixture()
        fixture.disconnectConfirmation = { fixture.order += "confirmed" }
        fixture.adapter.connect(fixture.channel)
        val oldHandler = requireNotNull(fixture.installedHandler) as JdaAudioSendHandler
        val oldStream = fixture.stream
        fixture.order.clear()
        every { fixture.audioManager.openAudioConnection(fixture.otherChannel) } answers {
            fixture.order += "open"
            fixture.markConnected()
            fixture.adapter.close()
        }

        // When: the reopen finishes after the close request.
        val result = fixture.adapter.reconnect(fixture.otherChannel)

        // Then: no false success and the freshly opened pipeline is torn down.
        assertEquals(JdaPlayerReconnectResult.NotConnected, result)
        assertNotEquals(JdaPlayerReconnectResult.Reconnected, result)
        assertEquals(listOf("close", "confirmed", "open", "close"), fixture.order)
        assertNull(fixture.installedHandler, "the post-open abort must detach the fresh handler")
        assertFalse(oldHandler.canProvide(), "the old handler must stay closed")
        assertTrue(oldStream.closeCalls > 0, "the old stream must be released")
        assertEquals(2, fixture.streams.size)
        assertTrue(fixture.streams.all { it.closeCalls > 0 }, "the post-open abort must release the fresh stream")
        verify(exactly = 1) { fixture.audioManager.openAudioConnection(fixture.otherChannel) }
        verify(exactly = 2) { fixture.audioManager.closeAudioConnection() }
        verify(exactly = 2) { fixture.audioManager.setSendingHandler(null) }
    }
}
