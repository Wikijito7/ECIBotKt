package es.wokis.voice

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Focused RFC 6716 framing tests for [OpusPacketValidator].
 *
 * Every packet is synthetic: only the TOC, frame-count and frame-length headers matter to the
 * validator, so no encoder, decoder or external asset is involved. The tests intentionally pin
 * both the accepted 20 ms shapes (code 0/1/2/3) and every rejection path the voice ports rely
 * on before an [AudioFrame] reaches the gateway send loop.
 */
class OpusPacketValidatorTest {

    @Test
    fun `Given a code 0 packet with one 20 ms frame When parsed Then it declares one 20 ms frame`() {
        // Given: config 15 is CELT 20 ms, code 0 is a single frame.
        val payload = bytes(0x78, 0x11, 0x22, 0x33)

        // When
        val framing = OpusPacketValidator.parse(payload)

        // Then
        assertEquals(OpusPacketValidator.Framing(frameCount = 1, durationSamples = 960), framing)
        assertEquals(framing, OpusPacketValidator.validate(payload))
    }

    @Test
    fun `Given a code 0 packet with a zero length DTX frame When parsed Then it is accepted`() {
        // Given: a lone TOC byte is a legal zero-length frame (DTX).
        val payload = bytes(0x78)

        // When
        val framing = OpusPacketValidator.parse(payload)

        // Then
        assertEquals(OpusPacketValidator.Framing(frameCount = 1, durationSamples = 960), framing)
    }

    @Test
    fun `Given a code 1 packet with two 10 ms CBR frames When validated Then it is 20 ms`() {
        // Given: config 0 is 10 ms, code 1 is two CBR frames; four frame bytes split evenly.
        val payload = bytes(0x01, 0xA1, 0xA2, 0xB1, 0xB2)

        // When
        val framing = OpusPacketValidator.validate(payload)

        // Then
        assertEquals(OpusPacketValidator.Framing(frameCount = 2, durationSamples = 960), framing)
    }

    @Test
    fun `Given a code 1 packet with zero length CBR frames When parsed Then it is 20 ms of DTX`() {
        // Given: code 1 with no frame bytes at all still declares two 10 ms frames.
        val payload = bytes(0x01)

        // When
        val framing = OpusPacketValidator.parse(payload)

        // Then
        assertEquals(OpusPacketValidator.Framing(frameCount = 2, durationSamples = 960), framing)
    }

    @Test
    fun `Given a code 1 packet with an odd frame-byte count When parsed Then it is rejected`() {
        // Given: three frame bytes cannot be split across two CBR frames.
        val payload = bytes(0x01, 0xA1, 0xA2, 0xB1)

        // When
        val error = assertThrows(IllegalArgumentException::class.java) {
            OpusPacketValidator.parse(payload)
        }

        // Then
        assertTrue(error.message.orEmpty().contains("even number"))
    }

    @Test
    fun `Given a code 2 packet with two 10 ms VBR frames When validated Then it is 20 ms`() {
        // Given: config 0 is 10 ms, code 2 is two VBR frames, both two bytes long.
        val payload = bytes(0x02, 0x02, 0xA1, 0xA2, 0xB1, 0xB2)

        // When
        val framing = OpusPacketValidator.validate(payload)

        // Then
        assertEquals(OpusPacketValidator.Framing(frameCount = 2, durationSamples = 960), framing)
    }

    @Test
    fun `Given a code 2 packet with unequal VBR frame sizes When parsed Then it is accepted`() {
        // Given: first frame one byte, second frame three bytes.
        val payload = bytes(0x02, 0x01, 0xA1, 0xB1, 0xB2, 0xB3)

        // When
        val framing = OpusPacketValidator.parse(payload)

        // Then
        assertEquals(OpusPacketValidator.Framing(frameCount = 2, durationSamples = 960), framing)
    }

    @Test
    fun `Given a code 2 packet with an empty second frame When parsed Then it is accepted`() {
        // Given: a two-byte first frame and a zero-byte second frame (DTX).
        val payload = bytes(0x02, 0x02, 0xA1, 0xA2)

        // When
        val framing = OpusPacketValidator.parse(payload)

        // Then
        assertEquals(OpusPacketValidator.Framing(frameCount = 2, durationSamples = 960), framing)
    }

    @Test
    fun `Given a code 2 packet with a two-byte frame length header When parsed Then it is accepted`() {
        // Given: the first frame length uses the 252..255 extended encoding.
        val payload = bytes(0x02, 0xFC, 0x00) + ByteArray(252)

        // When
        val framing = OpusPacketValidator.validate(payload)

        // Then
        assertEquals(OpusPacketValidator.Framing(frameCount = 2, durationSamples = 960), framing)
    }

    @Test
    fun `Given a code 2 packet with no frame length header When parsed Then it is rejected`() {
        // Given: code 2 requires a length byte after the TOC.
        val payload = bytes(0x02)

        // When
        val error = assertThrows(IllegalArgumentException::class.java) {
            OpusPacketValidator.parse(payload)
        }

        // Then
        assertTrue(error.message.orEmpty().contains("Truncated Opus frame length header"))
    }

    @Test
    fun `Given a code 2 packet with a truncated extended length When parsed Then it is rejected`() {
        // Given: the first length byte announces a second byte that is missing.
        val payload = bytes(0x02, 0xFC)

        // When
        val error = assertThrows(IllegalArgumentException::class.java) {
            OpusPacketValidator.parse(payload)
        }

        // Then
        assertTrue(error.message.orEmpty().contains("Truncated two-byte Opus frame length header"))
    }

    @Test
    fun `Given a code 2 packet whose first frame exceeds the payload When parsed Then it is rejected`() {
        // Given: a declared first frame longer than the remaining packet bytes.
        val payload = bytes(0x02, 0x0A, 0xA1)

        // When
        val error = assertThrows(IllegalArgumentException::class.java) {
            OpusPacketValidator.parse(payload)
        }

        // Then
        assertTrue(error.message.orEmpty().contains("do not fit"))
    }

    @Test
    fun `Given a code 3 packet with four 5 ms CBR frames When validated Then it is 20 ms`() {
        // Given: config 17 is 5 ms, code 3 declares four CBR frames.
        val payload = bytes(0x8B, 0x04, 1, 2, 3, 4, 5, 6, 7, 8)

        // When
        val framing = OpusPacketValidator.validate(payload)

        // Then
        assertEquals(OpusPacketValidator.Framing(frameCount = 4, durationSamples = 960), framing)
    }

    @Test
    fun `Given a code 3 packet with eight 2_5 ms CBR frames When validated Then it is 20 ms`() {
        // Given: config 16 is 2.5 ms, code 3 declares eight CBR frames.
        val payload = bytes(0x83, 0x08, 1, 2, 3, 4, 5, 6, 7, 8)

        // When
        val framing = OpusPacketValidator.validate(payload)

        // Then
        assertEquals(OpusPacketValidator.Framing(frameCount = 8, durationSamples = 960), framing)
    }

    @Test
    fun `Given a code 3 packet with four 5 ms VBR frames When validated Then it is 20 ms`() {
        // Given: VBR flag set, three length headers for four frames, six data bytes.
        val payload = bytes(0x8B, 0x84, 0x02, 0x02, 0x02, 1, 2, 3, 4, 5, 6)

        // When
        val framing = OpusPacketValidator.validate(payload)

        // Then
        assertEquals(OpusPacketValidator.Framing(frameCount = 4, durationSamples = 960), framing)
    }

    @Test
    fun `Given a code 3 packet with padding When validated Then the padding is excluded from frames`() {
        // Given: padding flag set, two padding bytes, eight data bytes for four CBR frames.
        val payload = bytes(0x8B, 0x44, 0x02, 1, 2, 3, 4, 5, 6, 7, 8, 0, 0)

        // When
        val framing = OpusPacketValidator.validate(payload)

        // Then
        assertEquals(OpusPacketValidator.Framing(frameCount = 4, durationSamples = 960), framing)
    }

    @Test
    fun `Given a code 3 packet with a multi-byte padding run When validated Then it is accepted`() {
        // Given: 0xFF contributes 254 padding bytes and the trailing zero adds none.
        val payload = bytes(0x83, 0x48, 0xFF, 0x00, 1, 2, 3, 4, 5, 6, 7, 8) + ByteArray(254)

        // When
        val framing = OpusPacketValidator.validate(payload)

        // Then
        assertEquals(OpusPacketValidator.Framing(frameCount = 8, durationSamples = 960), framing)
    }

    @Test
    fun `Given a code 3 packet with a missing frame count byte When parsed Then it is rejected`() {
        // Given: code 3 requires a second byte for flags and frame count.
        val payload = bytes(0x03)

        // When
        val error = assertThrows(IllegalArgumentException::class.java) {
            OpusPacketValidator.parse(payload)
        }

        // Then
        assertTrue(error.message.orEmpty().contains("must contain a frame count byte"))
    }

    @Test
    fun `Given a code 3 packet declaring zero frames When parsed Then it is rejected`() {
        // Given
        val payload = bytes(0x83, 0x00)

        // When
        val error = assertThrows(IllegalArgumentException::class.java) {
            OpusPacketValidator.parse(payload)
        }

        // Then
        assertTrue(error.message.orEmpty().contains("at least one frame"))
    }

    @Test
    fun `Given a code 3 packet whose VBR lengths exceed the payload When parsed Then it is rejected`() {
        // Given: a 250-byte first frame declared in a three-byte packet.
        val payload = bytes(0x83, 0x84, 0xFA)

        // When
        val error = assertThrows(IllegalArgumentException::class.java) {
            OpusPacketValidator.parse(payload)
        }

        // Then
        assertTrue(error.message.orEmpty().contains("exceed the packet payload"))
    }

    @Test
    fun `Given a code 3 packet with a truncated VBR length header When parsed Then it is rejected`() {
        // Given: the extended first-frame length byte has no second byte.
        val payload = bytes(0x83, 0x84, 0xFC)

        // When
        val error = assertThrows(IllegalArgumentException::class.java) {
            OpusPacketValidator.parse(payload)
        }

        // Then
        assertTrue(error.message.orEmpty().contains("Truncated two-byte Opus frame length header"))
    }

    @Test
    fun `Given a code 3 CBR packet that does not split evenly When parsed Then it is rejected`() {
        // Given: three data bytes cannot be split across four CBR frames.
        val payload = bytes(0x83, 0x04, 1, 2, 3)

        // When
        val error = assertThrows(IllegalArgumentException::class.java) {
            OpusPacketValidator.parse(payload)
        }

        // Then
        assertTrue(error.message.orEmpty().contains("must split"))
    }

    @Test
    fun `Given a code 3 packet with a truncated padding header When parsed Then it is rejected`() {
        // Given: the padding flag is set but no padding-length byte follows.
        val payload = bytes(0x83, 0x44)

        // When
        val error = assertThrows(IllegalArgumentException::class.java) {
            OpusPacketValidator.parse(payload)
        }

        // Then
        assertTrue(error.message.orEmpty().contains("Truncated Opus padding length header"))
    }

    @Test
    fun `Given a code 3 packet whose padding exceeds the payload When parsed Then it is rejected`() {
        // Given: ten padding bytes declared in a five-byte packet.
        val payload = bytes(0x83, 0x44, 0x0A, 0, 0)

        // When
        val error = assertThrows(IllegalArgumentException::class.java) {
            OpusPacketValidator.parse(payload)
        }

        // Then
        assertTrue(error.message.orEmpty().contains("does not fit the packet payload"))
    }

    @Test
    fun `Given a packet claiming more than 120 ms When parsed Then it is rejected`() {
        // Given: 63 frames of 2.5 ms is 157.5 ms, above the RFC 6716 maximum.
        val payload = bytes(0x83, 0x3F) + ByteArray(63)

        // When
        val error = assertThrows(IllegalArgumentException::class.java) {
            OpusPacketValidator.parse(payload)
        }

        // Then
        assertTrue(error.message.orEmpty().contains("more than 120 ms"))
    }

    @Test
    fun `Given a packet carrying exactly 120 ms When parsed Then the boundary is accepted`() {
        // Given: two 60 ms frames are exactly the RFC maximum, so parse accepts them.
        val payload = bytes(0x19, 0xA1, 0xA2, 0xB1, 0xB2)

        // When
        val framing = OpusPacketValidator.parse(payload)

        // Then
        assertEquals(OpusPacketValidator.Framing(frameCount = 2, durationSamples = 5760), framing)
        assertThrows(IllegalArgumentException::class.java) {
            OpusPacketValidator.validate(payload)
        }
    }

    @Test
    fun `Given an empty payload When parsed Then it is rejected`() {
        // When
        val error = assertThrows(IllegalArgumentException::class.java) {
            OpusPacketValidator.parse(ByteArray(0))
        }

        // Then
        assertTrue(error.message.orEmpty().contains("cannot be empty"))
    }

    @Test
    fun `Given a payload above the Opus packet limit When parsed Then it is rejected`() {
        // Given: one byte above the RFC 6716 maximum regardless of framing.
        val payload = ByteArray(OpusPacketValidator.MAX_PACKET_BYTES + 1).also { it[0] = 0x78 }

        // When
        val error = assertThrows(IllegalArgumentException::class.java) {
            OpusPacketValidator.parse(payload)
        }

        // Then
        assertTrue(error.message.orEmpty().contains(OpusPacketValidator.MAX_PACKET_BYTES.toString()))
    }

    @Test
    fun `Given a payload at the Opus packet limit When parsed Then it is accepted`() {
        // Given: code 1 with 1274 frame bytes, split evenly across two 10 ms frames.
        val payload = ByteArray(OpusPacketValidator.MAX_PACKET_BYTES).also { it[0] = 0x01 }

        // When
        val framing = OpusPacketValidator.validate(payload)

        // Then
        assertEquals(OpusPacketValidator.Framing(frameCount = 2, durationSamples = 960), framing)
    }

    @Test
    fun `Given a 40 ms packet When validated against 20 ms Then it is rejected`() {
        // Given: config 2 is 40 ms in a code 0 frame.
        val payload = bytes(0x10, 0xAA)

        // When
        val error = assertThrows(IllegalArgumentException::class.java) {
            OpusPacketValidator.validate(payload)
        }

        // Then
        assertTrue(error.message.orEmpty().contains("960"))
        assertTrue(error.message.orEmpty().contains("1920"))
        assertEquals(
            OpusPacketValidator.Framing(frameCount = 1, durationSamples = 1920),
            OpusPacketValidator.validate(payload, expectedDurationSamples = 1920),
        )
    }

    @Test
    fun `Given a 10 ms packet When validated against 20 ms Then it is rejected`() {
        // Given: config 0 is 10 ms in a code 0 frame.
        val payload = bytes(0x00, 0xAA)

        // When
        val error = assertThrows(IllegalArgumentException::class.java) {
            OpusPacketValidator.validate(payload)
        }

        // Then
        assertTrue(error.message.orEmpty().contains("480"))
    }

    private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { index ->
        values[index].toByte()
    }
}
