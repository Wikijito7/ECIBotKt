package es.wokis.jda.voice

import es.wokis.player.engine.audio.ContinuousPlayerAudioStream
import es.wokis.player.engine.audio.PlayerAudioState
import es.wokis.voice.AudioFrame
import es.wokis.voice.AudioFrameFormat
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class JdaContinuousAudioSendHandlerTest {

    @Test
    fun `Given a frame followed by paused silence When the handler polls Then both are handed to JDA`() {
        // Given
        val frame = AudioFrame.of(AudioFrameFormat.OPUS_20MS, byteArrayOf(0x78, 0x01))
        val stream = FakeContinuousStream(
            listOf(PlayerAudioState.Frame(frame), PlayerAudioState.PausedSilence),
        )
        val handler = JdaAudioSendHandler.continuous(stream)

        // When / Then
        assertTrue(handler.canProvide())
        val frameBuffer = requireNotNull(handler.provide20MsAudio())
        assertArrayEquals(byteArrayOf(0x78, 0x01), frameBuffer.array())

        assertTrue(handler.canProvide())
        val silenceBuffer = requireNotNull(handler.provide20MsAudio())
        assertArrayEquals(OPUS_SILENCE, silenceBuffer.array())
        assertEquals(2, handler.framesProvided)
    }

    @Test
    fun `Given sustained pause When the send loop keeps polling Then silence never exhausts the handler`() {
        // Given
        val stream = FakeContinuousStream(List(20) { PlayerAudioState.PausedSilence })
        val handler = JdaAudioSendHandler.continuous(stream)

        // When / Then
        repeat(20) {
            assertTrue(handler.canProvide(), "iteration $it must still provide silence")
            val buffer = requireNotNull(handler.provide20MsAudio())
            assertArrayEquals(OPUS_SILENCE, buffer.array())
        }
        assertEquals(20, handler.framesProvided)
        assertEquals(0, stream.closeCalls, "the handler must not close the caller-owned stream")
    }

    @Test
    fun `Given an ended stream When the handler polls Then it terminates without silence`() {
        // Given
        val stream = FakeContinuousStream(listOf(PlayerAudioState.Ended))
        val handler = JdaAudioSendHandler.continuous(stream)

        // When / Then
        assertFalse(handler.canProvide())
        assertEquals(0, handler.framesProvided)
        assertEquals(0, stream.closeCalls)
    }

    @Test
    fun `Given the continuous stream When queried Then the handler keeps the Opus contract`() {
        // Given
        val handler = JdaAudioSendHandler.continuous(FakeContinuousStream(emptyList()))

        // Then
        assertTrue(handler.isOpus())
    }

    private class FakeContinuousStream(
        private val states: List<PlayerAudioState>,
    ) : ContinuousPlayerAudioStream {
        private var index = 0
        var closeCalls = 0
            private set

        override fun next(): PlayerAudioState = states.getOrElse(index++) { PlayerAudioState.Ended }

        override fun close() {
            closeCalls++
        }
    }

    private companion object {
        val OPUS_SILENCE = byteArrayOf(0xFC.toByte(), 0xFF.toByte(), 0xFE.toByte())
    }
}
