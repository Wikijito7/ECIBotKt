package es.wokis.jda.voice

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.audio.hooks.ConnectionStatus
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.managers.AudioManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class JdaVoiceCleanupTest {

    @Test
    fun `Given an already shutdown client When disconnecting Then no guild is inspected`() {
        // Given
        val jda = mockk<JDA>()
        every { jda.status } returns JDA.Status.SHUTDOWN

        // When
        disconnectVoiceConnections(jda)

        // Then
        verify(exactly = 0) { jda.guilds }
    }

    @Test
    fun `Given connected transitioning and idle managers When disconnecting Then only busy connections are closed`() {
        // Given
        val closed = mutableListOf<String>()
        val connected = guild(isConnected = true, status = ConnectionStatus.CONNECTED) {
            closed += "connected"
        }
        val transitioning = guild(
            isConnected = false,
            status = ConnectionStatus.CONNECTING_AWAITING_READY,
        ) { closed += "transitioning" }
        val idle = guild(isConnected = false, status = ConnectionStatus.NOT_CONNECTED) {
            closed += "idle"
        }
        val jda = jdaWith(connected, transitioning, idle)

        // When
        disconnectVoiceConnections(jda)

        // Then
        assertEquals(listOf("connected", "transitioning"), closed)
    }

    @Test
    fun `Given one guild fails When disconnecting Then the remaining guilds are still closed`() {
        // Given
        val failure = IllegalStateException("cannot close the first guild")
        val closed = mutableListOf<String>()
        val failing = guild(isConnected = true, status = ConnectionStatus.CONNECTED) {
            throw failure
        }
        val healthy = guild(isConnected = true, status = ConnectionStatus.CONNECTED) {
            closed += "healthy"
        }
        val jda = jdaWith(failing, healthy)

        // When
        val thrown = assertThrows(IllegalStateException::class.java) {
            disconnectVoiceConnections(jda)
        }

        // Then
        assertSame(failure, thrown)
        assertEquals(listOf("healthy"), closed)
    }

    @Test
    fun `Given several normal failures When disconnecting Then the first is primary and the rest are suppressed`() {
        // Given
        val first = IllegalStateException("first")
        val second = IllegalArgumentException("second")
        val jda = jdaWith(
            guild(isConnected = true, status = ConnectionStatus.CONNECTED) { throw first },
            guild(isConnected = true, status = ConnectionStatus.CONNECTED) { throw second },
        )

        // When
        val thrown = assertThrows(IllegalStateException::class.java) {
            disconnectVoiceConnections(jda)
        }

        // Then
        assertSame(first, thrown)
        assertEquals(listOf(second), first.suppressed.toList())
    }

    @Test
    fun `Given an Error in a later guild When disconnecting Then the fatal failure is primary with normal failures suppressed`() {
        // Given
        val normal = IllegalStateException("normal")
        val fatal = AssertionError("fatal")
        val jda = jdaWith(
            guild(isConnected = true, status = ConnectionStatus.CONNECTED) { throw normal },
            guild(isConnected = true, status = ConnectionStatus.CONNECTED) { throw fatal },
        )

        // When
        val thrown = assertThrows(AssertionError::class.java) {
            disconnectVoiceConnections(jda)
        }

        // Then
        assertSame(fatal, thrown)
        assertEquals(listOf(normal), fatal.suppressed.toList())
    }

    @Test
    fun `Given a cancellation in a later guild When disconnecting Then the cancellation is primary with normal failures suppressed`() {
        // Given
        val normal = IllegalStateException("normal")
        val cancellation = CancellationException("cancelled")
        val jda = jdaWith(
            guild(isConnected = true, status = ConnectionStatus.CONNECTED) { throw normal },
            guild(isConnected = true, status = ConnectionStatus.CONNECTED) { throw cancellation },
        )

        // When
        val thrown = assertThrows(CancellationException::class.java) {
            disconnectVoiceConnections(jda)
        }

        // Then
        assertSame(cancellation, thrown)
        assertEquals(listOf(normal), cancellation.suppressed.toList())
    }

    @Test
    fun `Given no guilds When disconnecting Then nothing is closed and no failure is raised`() {
        // Given
        val jda = jdaWith()

        // When
        disconnectVoiceConnections(jda)

        // Then: reaching this point without an exception is the assertion.
    }

    private fun jdaWith(vararg guilds: Guild): JDA {
        val jda = mockk<JDA>()
        every { jda.status } returns JDA.Status.CONNECTED
        every { jda.guilds } returns guilds.toList()
        return jda
    }

    private fun guild(
        isConnected: Boolean,
        status: ConnectionStatus,
        close: () -> Unit,
    ): Guild {
        val manager = mockk<AudioManager>()
        val guild = mockk<Guild>()
        every { manager.isConnected } returns isConnected
        every { manager.connectionStatus } returns status
        every { manager.closeAudioConnection() } answers { close() }
        every { guild.audioManager } returns manager
        return guild
    }
}
