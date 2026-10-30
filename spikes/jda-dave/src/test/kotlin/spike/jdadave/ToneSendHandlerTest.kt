package spike.jdadave

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeoutException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ToneSendHandlerTest {

    @Test
    fun `frame size is exactly one 20 ms 48 kHz 16 bit stereo frame`() {
        assertEquals(3840, ToneSendHandler.FRAME_BYTES)
        assertEquals(48_000, ToneSendHandler.SAMPLE_RATE)
        assertEquals(2, ToneSendHandler.CHANNELS)
        assertEquals(16, ToneSendHandler.BITS_PER_SAMPLE)
        assertEquals(20, ToneSendHandler.FRAME_MILLIS)

        val handler = ToneSendHandler()

        repeat(handler.totalFrames) {
            val frame = assertNotNull(handler.provide20MsAudio())
            assertEquals(3840, frame.remaining())
            assertEquals(3840, frame.capacity())
        }
    }

    @Test
    fun `uses the default 1_5 second tone and big endian PCM`() {
        val handler = ToneSendHandler()

        assertEquals(75, handler.totalFrames)
        assertEquals(440.0, handler.frequencyHz)
        assertEquals(1_500, handler.durationMillis)
        assertEquals(0.10, handler.amplitude)
        assertFalse(handler.isOpus())

        val frame = assertNotNull(handler.provide20MsAudio())
        assertEquals(ByteOrder.BIG_ENDIAN, frame.order())
    }

    @Test
    fun `each frame contains 960 duplicated stereo samples`() {
        val handler = ToneSendHandler(durationMillis = 20)
        val frame = assertNotNull(handler.provide20MsAudio())
        val bytes = frame.toByteArray()

        assertEquals(960 * 2 * 2, bytes.size)

        val samples = decodeSamples(bytes)
        assertEquals(960 * 2, samples.size)
        for (sampleIndex in 0 until 960) {
            assertEquals(
                samples[sampleIndex * 2],
                samples[sampleIndex * 2 + 1],
                "left and right channels must carry the same sample at index $sampleIndex",
            )
        }

        // Interleaved big-endian decoding of the very first sample: both channels are
        // zero because the 10 ms fade starts at silence.
        assertEquals(0.toShort(), samples[0])
        assertEquals(0.toShort(), samples[1])
    }

    @Test
    fun `pre-rendered frames keep a continuous 440 Hz phase across frame boundaries`() {
        val handler = ToneSendHandler()
        val leftChannel = ArrayList<Short>(72_000)

        repeat(handler.totalFrames) {
            val bytes = assertNotNull(handler.provide20MsAudio()).toByteArray()
            assertEquals(ToneSendHandler.FRAME_BYTES, bytes.size)
            val samples = decodeSamples(bytes)
            for (sampleIndex in 0 until samples.size step 2) {
                leftChannel += samples[sampleIndex]
            }
        }

        assertEquals(72_000, leftChannel.size)
        assertEquals(0.toShort(), leftChannel.first())
        assertEquals(0.toShort(), leftChannel.last())

        val peak = leftChannel.maxOf { kotlin.math.abs(it.toInt()) }
        assertTrue(peak in 3_270..3_277, "unexpected peak amplitude $peak")

        // Every internal frame boundary must continue the sine instead of restarting it.
        val maxBoundaryJump = (1 until handler.totalFrames).maxOf { frameIndex ->
            val previous = leftChannel[frameIndex * 960 - 1].toInt()
            val next = leftChannel[frameIndex * 960].toInt()
            kotlin.math.abs(next - previous)
        }
        assertTrue(maxBoundaryJump <= 200, "frame boundary resets the phase (jump=$maxBoundaryJump)")

        val positiveZeroCrossings = leftChannel.zipWithNext().count { (previous, next) ->
            previous <= 0 && next > 0
        }
        assertEquals(660, positiveZeroCrossings)
    }

    @Test
    fun `exhaustion returns no frame and canProvide turns false`() {
        val handler = ToneSendHandler(durationMillis = 40)

        assertEquals(2, handler.totalFrames)
        assertTrue(handler.canProvide())
        assertNotNull(handler.provide20MsAudio())
        assertTrue(handler.canProvide())
        assertNotNull(handler.provide20MsAudio())

        assertFalse(handler.canProvide())
        assertEquals(null, handler.provide20MsAudio())
        assertEquals(2, handler.framesProvided)
        assertFalse(handler.canProvide())
    }

    @Test
    fun `framesProvided counts only frames handed to the caller`() {
        val handler = ToneSendHandler()

        assertEquals(0, handler.framesProvided)
        assertFailsWith<TimeoutException> { handler.awaitFinished(10, java.util.concurrent.TimeUnit.MILLISECONDS) }

        assertNotNull(handler.provide20MsAudio())
        assertEquals(1, handler.framesProvided)

        repeat(73) {
            assertNotNull(handler.provide20MsAudio())
        }
        assertEquals(74, handler.framesProvided)

        // The tone future is not complete until the last frame is handed over.
        assertFailsWith<TimeoutException> { handler.awaitFinished(10, java.util.concurrent.TimeUnit.MILLISECONDS) }

        assertNotNull(handler.provide20MsAudio())
        assertEquals(75, handler.framesProvided)

        // The future completes when the last buffer is handed out, not on any network
        // acknowledgement: this handler has no network at all.
        handler.awaitFinished(1, java.util.concurrent.TimeUnit.SECONDS)

        assertEquals(null, handler.provide20MsAudio())
        assertEquals(75, handler.framesProvided)
    }

    @Test
    fun `a single frame handler completes after the first frame`() {
        val handler = ToneSendHandler(durationMillis = 20)

        assertEquals(1, handler.totalFrames)
        assertTrue(handler.canProvide())

        val frame = assertNotNull(handler.provide20MsAudio())
        assertEquals(ToneSendHandler.FRAME_BYTES, frame.remaining())
        assertEquals(1, handler.framesProvided)
        assertFalse(handler.canProvide())

        handler.awaitFinished(1, java.util.concurrent.TimeUnit.SECONDS)
        assertEquals(null, handler.provide20MsAudio())
        assertEquals(1, handler.framesProvided)
    }

    @Test
    fun `zero amplitude renders silence`() {
        val handler = ToneSendHandler(durationMillis = 20, amplitude = 0.0)
        val bytes = assertNotNull(handler.provide20MsAudio()).toByteArray()

        assertEquals(ToneSendHandler.FRAME_BYTES, bytes.size)
        assertContentEquals(ByteArray(ToneSendHandler.FRAME_BYTES), bytes)
    }

    @Test
    fun `rejects durations that are not a positive multiple of 20 ms`() {
        listOf(0, -20, 15, 39).forEach { duration ->
            assertFailsWith<IllegalArgumentException>("duration=$duration") {
                ToneSendHandler(durationMillis = duration)
            }
        }
    }

    @Test
    fun `rejects amplitudes outside 0 to 1`() {
        listOf(-0.01, 1.01, -1.0, 2.0).forEach { amplitude ->
            assertFailsWith<IllegalArgumentException>("amplitude=$amplitude") {
                ToneSendHandler(amplitude = amplitude)
            }
        }
    }

    private fun ByteBuffer.toByteArray(): ByteArray {
        val copy = duplicate()
        val bytes = ByteArray(copy.remaining())
        copy.get(bytes)
        return bytes
    }

    private fun decodeSamples(bytes: ByteArray): List<Short> =
        (0 until bytes.size / 2).map { sampleIndex ->
            (
                (bytes[sampleIndex * 2].toInt() shl 8) or
                    (bytes[sampleIndex * 2 + 1].toInt() and 0xFF)
                ).toShort()
        }
}
