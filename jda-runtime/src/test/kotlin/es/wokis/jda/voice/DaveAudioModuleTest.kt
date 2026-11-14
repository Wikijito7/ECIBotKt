package es.wokis.jda.voice

import io.mockk.mockk
import io.mockk.verify
import moe.kyokobot.libdave.DaveFactory
import moe.kyokobot.libdave.jda.LDJDADaveSessionFactory
import net.dv8tion.jda.api.audio.factory.DefaultSendFactory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.test.assertIs

class DaveAudioModuleTest {

    @Test
    fun `Given a positive protocol version When validated Then it is returned unchanged`() {
        // When
        val version = DaveAudioModule.requirePositiveProtocolVersion(1)

        // Then
        assertEquals(1, version)
        assertEquals(42, DaveAudioModule.requirePositiveProtocolVersion(42))
    }

    @Test
    fun `Given a non-positive protocol version When validated Then the runtime fails fast`() {
        // When
        listOf(0, -1, Int.MIN_VALUE).forEach { version ->
            val error = assertThrows(IllegalStateException::class.java) {
                DaveAudioModule.requirePositiveProtocolVersion(version)
            }

            // Then
            assertTrue(error.message.orEmpty().contains("maxSupportedProtocolVersion=$version"))
            assertTrue(error.message.orEmpty().contains("DAVE cannot be negotiated"))
        }
    }

    @Test
    fun `Given a DAVE factory When the audio module config is built Then the JDA DAVE session factory is wired`() {
        // Given: a strict mock that throws on any unexpected interaction, so building the
        // config must not load or query the native implementation.
        val factory = mockk<DaveFactory>()

        // When
        val config = DaveAudioModule.audioModuleConfig(factory)

        // Then
        assertIs<LDJDADaveSessionFactory>(config.daveSessionFactory)
        assertIs<DefaultSendFactory>(
            config.audioSendFactory,
            "the adapter must keep JDA's default Opus send factory",
        )
        verify(exactly = 0) { factory.maxSupportedProtocolVersion() }
        verify(exactly = 0) { factory.createDecryptor() }
        verify(exactly = 0) { factory.createEncryptor() }
        verify(exactly = 0) { factory.createSession(any(), any(), any()) }
    }

    @Test
    fun `Given a DAVE factory When the audio module config is built twice Then each call gets its own factory wrapper`() {
        // Given
        val factory = mockk<DaveFactory>()

        // When
        val first = DaveAudioModule.audioModuleConfig(factory)
        val second = DaveAudioModule.audioModuleConfig(factory)

        // Then
        assertNotSame(first, second)
        assertIs<LDJDADaveSessionFactory>(first.daveSessionFactory)
        assertIs<LDJDADaveSessionFactory>(second.daveSessionFactory)
        assertTrue(first.daveSessionFactory !== second.daveSessionFactory)
    }
}
