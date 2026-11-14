package es.wokis.voice

import es.wokis.domain.model.ChannelId
import es.wokis.player.PlayerMessageId
import es.wokis.player.PlayerMessagePort
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class VoicePortsTest {

    @Test
    fun `Given a payload When a frame is built Then the source array is copied`() {
        // Given: a well-formed 20 ms code-0 Opus packet.
        val payload = opus20msPacket(0x01, 0x02)

        // When
        val frame = AudioFrame.of(AudioFrameFormat.OPUS_20MS, payload)

        // Then
        assertEquals(AudioFrameFormat.OPUS_20MS, frame.format)
        assertEquals(3, frame.size)
        assertArrayEquals(opus20msPacket(0x01, 0x02), frame.payloadCopy())

        // The caller may reuse or mutate its source array after construction: neither the
        // stored frame, a diagnostic copy nor a later send view observes that mutation.
        payload.fill(0x00)
        assertArrayEquals(opus20msPacket(0x01, 0x02), frame.payloadCopy())
        assertArrayEquals(opus20msPacket(0x01, 0x02), frame.payloadBuffer().array())
        assertEquals(3, frame.size)
    }

    @Test
    fun `Given a frame When payloadCopy is called twice Then each call returns an isolated array`() {
        // Given: a well-formed 20 ms code-0 Opus packet.
        val payload = opus20msPacket(0x11, 0x22, 0x33)
        val frame = AudioFrame.of(AudioFrameFormat.OPUS_20MS, payload)

        // When
        val first = frame.payloadCopy()
        val second = frame.payloadCopy()

        // Then
        assertNotSame(first, second, "every payload copy must be a distinct array")
        assertArrayEquals(opus20msPacket(0x11, 0x22, 0x33), first)
        assertArrayEquals(opus20msPacket(0x11, 0x22, 0x33), second)

        // Mutating a copy cannot reach the stored frame or any other copy.
        first.fill(0x00)
        assertArrayEquals(opus20msPacket(0x11, 0x22, 0x33), second)
        assertArrayEquals(opus20msPacket(0x11, 0x22, 0x33), frame.payloadCopy())
        assertEquals(4, frame.size)
    }

    @Test
    fun `Given a frame When a payload buffer is mutated Then later views keep the original bytes`() {
        // Given: a well-formed 20 ms code-0 Opus packet.
        val payload = opus20msPacket(0x11, 0x22, 0x33)
        val frame = AudioFrame.of(AudioFrameFormat.OPUS_20MS, payload)

        // When
        val first = frame.payloadBuffer()
        val second = frame.payloadBuffer()

        // Then: the send loop receives an array-backed buffer over exactly the frame bytes.
        assertTrue(first.hasArray(), "the send loop must receive an array-backed buffer")
        assertEquals(0, first.arrayOffset())
        assertEquals(0, first.position())
        assertEquals(frame.size, first.remaining())
        assertArrayEquals(frame.payloadCopy(), first.array())

        // Each call owns its backing array, so buffers never alias each other.
        assertNotSame(first.array(), second.array(), "buffers must not share a backing array")
        assertEquals(0, second.position())
        assertEquals(frame.size, second.remaining())

        // Mutating one buffer, directly or through its backing array, cannot corrupt the
        // stored frame or any later view.
        first.array().fill(0x00)
        assertArrayEquals(opus20msPacket(0x11, 0x22, 0x33), second.array())
        assertArrayEquals(opus20msPacket(0x11, 0x22, 0x33), frame.payloadCopy())
        assertArrayEquals(opus20msPacket(0x11, 0x22, 0x33), frame.payloadBuffer().array())
        assertEquals(4, frame.size)

        // Consuming one buffer never moves the next view: every call starts at zero.
        while (second.hasRemaining()) {
            second.get()
        }
        assertEquals(frame.size, second.position())
        assertEquals(0, frame.payloadBuffer().position())
        assertEquals(frame.size, frame.payloadBuffer().remaining())
        assertEquals(4, frame.size)
    }

    @Test
    fun `Given an empty payload When a frame is built Then it is rejected`() {
        // When
        val error = assertThrows(IllegalArgumentException::class.java) {
            AudioFrame.of(AudioFrameFormat.OPUS_20MS, ByteArray(0))
        }

        // Then
        assertTrue(error.message.orEmpty().contains("cannot be empty"))
    }

    @Test
    fun `Given a payload above the Opus packet limit When a frame is built Then it is rejected`() {
        // Given: a structurally valid 20 ms packet that is one byte over the limit.
        val oversized = ByteArray(AudioFrame.MAX_OPUS_PACKET_BYTES + 1).also { it[0] = 0x78 }

        // When
        val error = assertThrows(IllegalArgumentException::class.java) {
            AudioFrame.of(AudioFrameFormat.OPUS_20MS, oversized)
        }

        // Then
        assertTrue(error.message.orEmpty().contains(AudioFrame.MAX_OPUS_PACKET_BYTES.toString()))
    }

    @Test
    fun `Given the exact Opus packet limit When a frame is built Then it is accepted`() {
        // Given: a code-1 packet whose 1274 frame bytes split evenly across two 10 ms frames.
        val payload = ByteArray(AudioFrame.MAX_OPUS_PACKET_BYTES).also { it[0] = 0x01 }

        // When
        val frame = AudioFrame.of(AudioFrameFormat.OPUS_20MS, payload)

        // Then
        assertEquals(1275, AudioFrame.MAX_OPUS_PACKET_BYTES)
        assertEquals(AudioFrame.MAX_OPUS_PACKET_BYTES, frame.size)
    }

    @Test
    fun `Given the Opus format When inspected Then it is twenty milliseconds of pre-encoded audio`() {
        // Then
        assertEquals(20, AudioFrameFormat.OPUS_20MS.frameDurationMillis)
        assertTrue(AudioFrameFormat.OPUS_20MS.isOpus)
    }

    @Test
    fun `Given a simple fake source When drained Then it returns its frames and then null forever`() {
        // Given
        val source = FakeAudioFrameSource(frameCount = 3)

        // When
        val frames = generateSequence { source.nextFrame() }.toList()

        // Then
        assertEquals(3, source.expectedFrameCount)
        assertEquals(AudioFrameFormat.OPUS_20MS, source.format)
        assertEquals(3, frames.size)
        frames.forEach { assertEquals(AudioFrameFormat.OPUS_20MS, it.format) }
        assertNull(source.nextFrame())
        assertNull(source.nextFrame())

        // Closing is idempotent and leaves the source exhausted.
        source.close()
        source.close()
        assertNull(source.nextFrame())
    }

    @Test
    fun `Given a closed fake source When next frame is requested Then it returns null`() {
        // Given
        val source = FakeAudioFrameSource(frameCount = 2)

        // When
        source.close()

        // Then
        assertNull(source.nextFrame())
    }

    @Test
    fun `Given a text-only fake port When send edit and delete are used Then the exact values reach it`() = runTest {
        // Given
        val port = RecordingPlayerMessagePort()
        val channelId = ChannelId(987654321uL)

        // When
        val messageId = port.send(channelId, "Now playing")
        port.edit(channelId, messageId, "Finished")
        port.delete(channelId, messageId)

        // Then
        assertEquals(
            listOf(
                "send:987654321:Now playing",
                "edit:987654321:${messageId.value}:Finished",
                "delete:987654321:${messageId.value}",
            ),
            port.operations,
        )
        assertEquals(PlayerMessageId(555uL), messageId)
    }

    @Test
    fun `Given the full unsigned message id When rendered Then the decimal value is preserved`() {
        // Then
        assertEquals(
            "18446744073709551615",
            PlayerMessageId(ULong.MAX_VALUE).toString(),
        )
        assertEquals("0", PlayerMessageId(0uL).toString())
    }

    @Test
    fun `Given a playback result When compared Then it is a plain value object`() {
        // Then
        assertEquals(VoicePlaybackResult.Completed(75, 75), VoicePlaybackResult.Completed(75, 75))
        assertEquals(
            VoicePlaybackResult.Rejected(VoiceRequestRejection.DIRECT_MESSAGE),
            VoicePlaybackResult.Rejected(VoiceRequestRejection.DIRECT_MESSAGE),
        )
        assertEquals(
            VoicePlaybackResult.Failed(VoiceFailureReason.TONE_TIMEOUT),
            VoicePlaybackResult.Failed(VoiceFailureReason.TONE_TIMEOUT),
        )
        assertFalse(VoicePlaybackResult.Completed(1, 2) == VoicePlaybackResult.Completed(2, 1))
    }

    private class FakeAudioFrameSource(frameCount: Int) : AudioFrameSource {
        private val remaining = AtomicInteger(frameCount)
        private val closed = AtomicBoolean(false)

        override val format: AudioFrameFormat = AudioFrameFormat.OPUS_20MS
        override val expectedFrameCount: Int = frameCount

        override fun nextFrame(): AudioFrame? {
            if (closed.get()) {
                return null
            }
            return if (remaining.getAndDecrement() > 0) {
                AudioFrame.of(AudioFrameFormat.OPUS_20MS, opus20msPacket())
            } else {
                null
            }
        }

        override fun close() {
            closed.set(true)
        }
    }

    private class RecordingPlayerMessagePort : PlayerMessagePort {
        val operations = mutableListOf<String>()

        override suspend fun send(channelId: ChannelId, content: String): PlayerMessageId {
            operations += "send:${channelId.value}:$content"
            return PlayerMessageId(555uL)
        }

        override suspend fun edit(channelId: ChannelId, messageId: PlayerMessageId, content: String) {
            operations += "edit:${channelId.value}:${messageId.value}:$content"
        }

        override suspend fun delete(channelId: ChannelId, messageId: PlayerMessageId) {
            operations += "delete:${channelId.value}:${messageId.value}"
        }
    }
}

/**
 * Builds a minimal well-formed 20 ms Opus packet: config 15 (CELT 20 ms) with code 0
 * (a single frame). The optional bytes are arbitrary frame data, not an encoding.
 */
private fun opus20msPacket(vararg frameBytes: Int): ByteArray =
    ByteArray(frameBytes.size + 1).also { packet ->
        packet[0] = 0x78
        frameBytes.forEachIndexed { index, value -> packet[index + 1] = value.toByte() }
    }
