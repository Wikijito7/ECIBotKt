package es.wokis.jda.voice

import net.dv8tion.jda.api.audio.hooks.ConnectionStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.test.assertContains
import kotlin.test.assertIs

class VoiceConnectionWaiterTest {

    @Test
    fun `Given a fresh waiter When inspected Then it starts idle and does not claim a pending connection`() {
        val waiter = VoiceConnectionWaiter()

        assertEquals(VoiceConnectionWaiter.ConnectionState.IDLE, waiter.connectionState())
        assertFalse(waiter.hasPendingConnectionRequest())
        assertNull(waiter.terminalMessage())
        waiter.checkTerminal()
    }

    @Test
    fun `Given a requested connection When CONNECTED arrives Then the wait succeeds`() {
        val waiter = VoiceConnectionWaiter()
        waiter.markRequested()
        assertEquals(VoiceConnectionWaiter.ConnectionState.CONNECTING, waiter.connectionState())
        assertTrue(waiter.hasPendingConnectionRequest())

        waiter.onStatusChange(ConnectionStatus.CONNECTED)
        waiter.awaitConnected(50, TimeUnit.MILLISECONDS)

        assertEquals(VoiceConnectionWaiter.ConnectionState.CONNECTED, waiter.connectionState())
        assertFalse(waiter.hasPendingConnectionRequest())
        assertNull(waiter.terminalMessage())
        waiter.checkTerminal()
    }

    @Test
    fun `Given a connected waiter When markPlaying is called Then only a connected session becomes playing`() {
        val waiter = VoiceConnectionWaiter()
        waiter.markRequested()

        // When: before CONNECTED the transition must not fabricate a playing session
        waiter.markPlaying()

        assertEquals(VoiceConnectionWaiter.ConnectionState.CONNECTING, waiter.connectionState())

        waiter.onStatusChange(ConnectionStatus.CONNECTED)
        waiter.markPlaying()

        assertEquals(VoiceConnectionWaiter.ConnectionState.PLAYING, waiter.connectionState())
    }

    @Test
    fun `Given no status When awaitConnected times out Then it throws a timeout and records no failure`() {
        val waiter = VoiceConnectionWaiter()
        waiter.markRequested()

        val error = assertThrows(TimeoutException::class.java) {
            waiter.awaitConnected(5, TimeUnit.MILLISECONDS)
        }

        assertContains(error.message.orEmpty(), "did not reach CONNECTED")
        assertNull(waiter.terminalMessage())
        waiter.checkTerminal()
    }

    @Test
    fun `Given an authentication failure When awaited Then it fails with the terminal reason`() {
        val waiter = VoiceConnectionWaiter()
        waiter.markRequested()

        waiter.onStatusChange(ConnectionStatus.DISCONNECTED_AUTHENTICATION_FAILURE)
        val thrown = assertThrows(VoiceConnectionException::class.java) {
            waiter.awaitConnected(5, TimeUnit.SECONDS)
        }

        assertContains(thrown.message.orEmpty(), "authentication")
        assertContains(waiter.terminalMessage().orEmpty(), "authentication")
        val check = assertThrows(VoiceConnectionException::class.java) { waiter.checkTerminal() }
        assertContains(check.message.orEmpty(), "authentication")
    }

    @Test
    fun `Given an audio region change When awaited Then it fails promptly`() {
        val waiter = VoiceConnectionWaiter()
        waiter.markRequested()

        waiter.onStatusChange(ConnectionStatus.AUDIO_REGION_CHANGE)

        val thrown = assertThrows(VoiceConnectionException::class.java) {
            waiter.awaitConnected(5, TimeUnit.SECONDS)
        }
        assertContains(thrown.message.orEmpty(), "audio region")
        assertContains(waiter.terminalMessage().orEmpty(), "audio region")
    }

    @Test
    fun `Given a shutting down status When awaited Then it is terminal`() {
        val waiter = VoiceConnectionWaiter()
        waiter.markRequested()

        waiter.onStatusChange(ConnectionStatus.SHUTTING_DOWN)

        val thrown = assertThrows(VoiceConnectionException::class.java) {
            waiter.awaitConnected(5, TimeUnit.SECONDS)
        }
        assertContains(thrown.message.orEmpty(), "shut down")
    }

    @Test
    fun `Given a deleted channel status When awaited Then it is terminal`() {
        val waiter = VoiceConnectionWaiter()
        waiter.markRequested()

        waiter.onStatusChange(ConnectionStatus.DISCONNECTED_CHANNEL_DELETED)

        val thrown = assertThrows(VoiceConnectionException::class.java) {
            waiter.awaitConnected(5, TimeUnit.SECONDS)
        }
        assertContains(thrown.message.orEmpty(), "channel was deleted")
    }

    @Test
    fun `Given an ERROR status When awaited Then it is terminal`() {
        val waiter = VoiceConnectionWaiter()
        waiter.markRequested()

        waiter.onStatusChange(ConnectionStatus.ERROR_CONNECTION_TIMEOUT)

        val thrown = assertThrows(VoiceConnectionException::class.java) {
            waiter.awaitConnected(5, TimeUnit.SECONDS)
        }
        assertContains(thrown.message.orEmpty(), "Voice connection failed")
    }

    @Test
    fun `Given the first terminal status When later failures arrive Then the first reason is kept`() {
        val waiter = VoiceConnectionWaiter()
        waiter.markRequested()

        waiter.onStatusChange(ConnectionStatus.DISCONNECTED_AUTHENTICATION_FAILURE)
        waiter.onStatusChange(ConnectionStatus.ERROR_LOST_CONNECTION)

        assertContains(waiter.terminalMessage().orEmpty(), "authentication")
        val thrown = assertThrows(VoiceConnectionException::class.java) { waiter.checkTerminal() }
        assertContains(thrown.message.orEmpty(), "authentication")
    }

    @Test
    fun `Given a healthy CONNECTED When no terminal status arrives Then no failure is reported`() {
        val waiter = VoiceConnectionWaiter()
        waiter.markRequested()

        waiter.onStatusChange(ConnectionStatus.CONNECTED)
        waiter.awaitConnected(50, TimeUnit.MILLISECONDS)

        waiter.checkTerminal()
        assertNull(waiter.terminalMessage())
    }

    @Test
    fun `Given a terminal status after CONNECTED When checked Then the loss is reported`() {
        val waiter = VoiceConnectionWaiter()
        waiter.markRequested()
        waiter.onStatusChange(ConnectionStatus.CONNECTED)
        waiter.awaitConnected(50, TimeUnit.MILLISECONDS)
        waiter.checkTerminal()

        waiter.onStatusChange(ConnectionStatus.AUDIO_REGION_CHANGE)

        val thrown = assertThrows(VoiceConnectionException::class.java) { waiter.checkTerminal() }
        assertContains(thrown.message.orEmpty(), "audio region")
    }

    @Test
    fun `Given a requested disconnect When NOT_CONNECTED arrives Then it confirms the request`() {
        val waiter = VoiceConnectionWaiter()
        waiter.markRequested()
        waiter.onStatusChange(ConnectionStatus.CONNECTED)
        waiter.awaitConnected(50, TimeUnit.MILLISECONDS)

        val request = waiter.expectDisconnect()
        assertEquals(VoiceConnectionWaiter.ConnectionState.DISCONNECTING, waiter.connectionState())

        waiter.onStatusChange(ConnectionStatus.NOT_CONNECTED)

        assertEquals(VoiceConnectionWaiter.ConnectionState.DISCONNECTED, waiter.connectionState())
        assertTrue(request.await(50, TimeUnit.MILLISECONDS))
        waiter.checkTerminal()
        assertNull(waiter.terminalMessage())
        assertFalse(waiter.hasPendingConnectionRequest())
    }

    @Test
    fun `Given an unexpected NOT_CONNECTED while connecting Then it is a terminal loss`() {
        val waiter = VoiceConnectionWaiter()
        waiter.markRequested()

        waiter.onStatusChange(ConnectionStatus.NOT_CONNECTED)

        assertEquals(VoiceConnectionWaiter.ConnectionState.CONNECTING, waiter.connectionState())
        assertTrue(waiter.hasPendingConnectionRequest(), "a never-connected request stays cancellable")
        val thrown = assertThrows(VoiceConnectionException::class.java) { waiter.checkTerminal() }
        assertContains(thrown.message.orEmpty(), "closed before it reached CONNECTED")
    }

    @Test
    fun `Given an unexpected NOT_CONNECTED while playing Then it is a terminal loss`() {
        val waiter = VoiceConnectionWaiter()
        waiter.markRequested()
        waiter.onStatusChange(ConnectionStatus.CONNECTED)
        waiter.awaitConnected(50, TimeUnit.MILLISECONDS)
        waiter.markPlaying()

        waiter.onStatusChange(ConnectionStatus.NOT_CONNECTED)

        assertEquals(VoiceConnectionWaiter.ConnectionState.PLAYING, waiter.connectionState())
        assertContains(waiter.terminalMessage().orEmpty(), "lost before cleanup")
        val thrown = assertThrows(VoiceConnectionException::class.java) { waiter.checkTerminal() }
        assertContains(thrown.message.orEmpty(), "lost before cleanup")
    }

    @Test
    fun `Given a stale NOT_CONNECTED while idle When a disconnect is requested Then only a fresh status confirms it`() {
        val waiter = VoiceConnectionWaiter()

        // When: the stale status predates any local request
        waiter.onStatusChange(ConnectionStatus.NOT_CONNECTED)

        assertEquals(VoiceConnectionWaiter.ConnectionState.IDLE, waiter.connectionState())
        val request = waiter.expectDisconnect()
        assertFalse(request.await(5, TimeUnit.MILLISECONDS))

        waiter.onStatusChange(ConnectionStatus.NOT_CONNECTED)

        assertEquals(VoiceConnectionWaiter.ConnectionState.DISCONNECTED, waiter.connectionState())
        assertTrue(request.await(50, TimeUnit.MILLISECONDS))
        waiter.checkTerminal()
        assertNull(waiter.terminalMessage())
    }

    @Test
    fun `Given a completed request When a duplicate NOT_CONNECTED arrives Then it cannot pre-confirm the next request`() {
        val waiter = VoiceConnectionWaiter()
        waiter.markRequested()
        waiter.onStatusChange(ConnectionStatus.CONNECTED)
        waiter.awaitConnected(50, TimeUnit.MILLISECONDS)
        val first = waiter.expectDisconnect()
        waiter.onStatusChange(ConnectionStatus.NOT_CONNECTED)
        assertTrue(first.await(50, TimeUnit.MILLISECONDS))

        waiter.onStatusChange(ConnectionStatus.NOT_CONNECTED)
        val second = waiter.expectDisconnect()

        assertFalse(second.await(5, TimeUnit.MILLISECONDS))
        waiter.onStatusChange(ConnectionStatus.NOT_CONNECTED)
        assertTrue(second.await(50, TimeUnit.MILLISECONDS))
    }

    @Test
    fun `Given a request that never connects When no status arrives Then its disconnect times out`() {
        val waiter = VoiceConnectionWaiter()
        waiter.markRequested()

        val request = waiter.expectDisconnect()

        assertFalse(request.await(5, TimeUnit.MILLISECONDS))
        assertEquals(VoiceConnectionWaiter.ConnectionState.DISCONNECTING, waiter.connectionState())
    }

    @Test
    fun `Given harmless connecting statuses When awaited Then none of them fails or succeeds early`() {
        val waiter = VoiceConnectionWaiter()
        waiter.markRequested()

        waiter.onStatusChange(ConnectionStatus.CONNECTING_AWAITING_ENDPOINT)
        waiter.onStatusChange(ConnectionStatus.CONNECTING_AWAITING_WEBSOCKET_CONNECT)
        waiter.onStatusChange(ConnectionStatus.CONNECTING_AWAITING_AUTHENTICATION)
        waiter.onStatusChange(ConnectionStatus.CONNECTING_ATTEMPTING_UDP_DISCOVERY)
        waiter.onStatusChange(ConnectionStatus.CONNECTING_AWAITING_READY)

        waiter.checkTerminal()
        assertNull(waiter.terminalMessage())
        assertEquals(VoiceConnectionWaiter.ConnectionState.CONNECTING, waiter.connectionState())
        assertThrows(TimeoutException::class.java) {
            waiter.awaitConnected(5, TimeUnit.MILLISECONDS)
        }
    }

    @Test
    fun `Given a NOT_CONNECTED while connecting When checked Then the recorded message is a failure`() {
        val waiter = VoiceConnectionWaiter()
        waiter.markRequested()

        waiter.onStatusChange(ConnectionStatus.NOT_CONNECTED)

        val failure = assertIs<VoiceConnectionException>(
            runCatching { waiter.checkTerminal() }.exceptionOrNull(),
        )
        assertContains(failure.message.orEmpty(), "closed before it reached CONNECTED")
    }
}
