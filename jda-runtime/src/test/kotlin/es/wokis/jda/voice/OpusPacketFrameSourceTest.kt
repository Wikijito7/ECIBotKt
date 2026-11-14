package es.wokis.jda.voice

import es.wokis.voice.AudioFrameFormat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class OpusPacketFrameSourceTest {

    @Test
    fun `Given the committed tone resource When loaded Then it contains 75 valid 20 ms Opus packets`() {
        // When
        val source = OpusPacketFrameSource.fromResource()

        // Then
        assertEquals(AudioFrameFormat.OPUS_20MS, source.format)
        assertEquals(75, source.expectedFrameCount)

        var frames = 0
        while (true) {
            val frame = source.nextFrame() ?: break
            frames++
            val packet = frame.payloadCopy()

            assertTrue(packet.isNotEmpty(), "packet $frames must not be empty")
            assertTrue(
                packet.size <= 1275,
                "packet $frames is ${packet.size} bytes, above the Opus packet limit",
            )
            assertTrue(
                packet.size <= es.wokis.voice.AudioFrame.MAX_OPUS_PACKET_BYTES,
                "packet $frames exceeds the shared neutral limit",
            )
            assertEquals(
                20.0,
                opusPacketDurationMillis(packet),
                1e-9,
                "packet $frames does not contain exactly 20 ms (TOC=0x${packet[0].toString(16)})",
            )
        }

        assertEquals(75, frames)
    }

    @Test
    fun `Given the committed resource When drained Then it returns null forever after the last frame`() {
        // Given
        val source = OpusPacketFrameSource.fromResource()
        repeat(source.expectedFrameCount) { source.nextFrame() }

        // Then
        assertNull(source.nextFrame())
        assertNull(source.nextFrame())
        assertNull(source.nextFrame())
    }

    @Test
    fun `Given a closed source When nextFrame is called Then it returns null`() {
        // Given
        val source = OpusPacketFrameSource.fromResource()

        // When
        source.close()

        // Then
        assertNull(source.nextFrame())
    }

    @Test
    fun `Given a valid single packet resource When loaded Then it exposes one frame`() {
        // Given
        val packet = opus20msPacket(0x01, 0x02, 0x03)
        val loader = BytesClassLoader(mapOf("single.opuspackets" to lengthPrefixed(packet)))

        // When
        val source = OpusPacketFrameSource.fromResource("single.opuspackets", loader)

        // Then
        assertEquals(1, source.expectedFrameCount)
        val frame = source.nextFrame()
        assertEquals(4, frame?.size)
        assertNull(source.nextFrame())
    }

    @Test
    fun `Given a missing resource When loaded Then it fails with a clear message`() {
        // When
        val error = assertThrows(IllegalStateException::class.java) {
            OpusPacketFrameSource.fromResource("missing.opuspackets", BytesClassLoader(emptyMap()))
        }

        // Then
        assertTrue(error.message.orEmpty().contains("Missing walkthrough tone resource"))
        assertTrue(error.message.orEmpty().contains("missing.opuspackets"))
    }

    @Test
    fun `Given an empty resource When loaded Then it is rejected`() {
        // When
        val error = assertThrows(IllegalStateException::class.java) {
            OpusPacketFrameSource.fromResource("empty.opuspackets", loaderFor(ByteArray(0)))
        }

        // Then
        assertTrue(error.message.orEmpty().contains("empty"))
    }

    @Test
    fun `Given a truncated length prefix When loaded Then it is rejected`() {
        // Given
        val resource = byteArrayOf(0x00)

        // When
        val error = assertThrows(IllegalStateException::class.java) {
            OpusPacketFrameSource.fromResource("malformed.opuspackets", loaderFor(resource))
        }

        // Then
        assertTrue(error.message.orEmpty().contains("Truncated packet length"))
    }

    @Test
    fun `Given a zero packet length When loaded Then it is rejected`() {
        // Given
        val resource = byteArrayOf(0x00, 0x00)

        // When
        val error = assertThrows(IllegalStateException::class.java) {
            OpusPacketFrameSource.fromResource("malformed.opuspackets", loaderFor(resource))
        }

        // Then
        assertTrue(error.message.orEmpty().contains("Invalid Opus packet length 0"))
    }

    @Test
    fun `Given a packet above the Opus limit When loaded Then it is rejected before allocation`() {
        // Given: a declared length of 1276 bytes with no payload at all.
        val resource = byteArrayOf((1276 shr 8).toByte(), (1276 and 0xFF).toByte())

        // When
        val error = assertThrows(IllegalStateException::class.java) {
            OpusPacketFrameSource.fromResource("malformed.opuspackets", loaderFor(resource))
        }

        // Then
        assertTrue(error.message.orEmpty().contains("Invalid Opus packet length 1276"))
    }

    @Test
    fun `Given a truncated packet payload When loaded Then it is rejected`() {
        // Given: the prefix declares 4 bytes but only 2 are present.
        val resource = byteArrayOf(0x00, 0x04, 0x01, 0x02)

        // When
        val error = assertThrows(IllegalStateException::class.java) {
            OpusPacketFrameSource.fromResource("malformed.opuspackets", loaderFor(resource))
        }

        // Then
        assertTrue(error.message.orEmpty().contains("Truncated Opus packet"))
    }

    @Test
    fun `Given readers held at a barrier When close returns Then none of their nextFrame calls yields a frame`() {
        // Given: eight readers are parked at a barrier before reading anything.
        val source = OpusPacketFrameSource.fromResource()
        val readerCount = 8
        val readsPerReader = 64
        val ready = CountDownLatch(readerCount)
        val resume = CountDownLatch(1)
        val reads = AtomicInteger(0)
        val framesYielded = AtomicInteger(0)
        val pool = Executors.newFixedThreadPool(readerCount)

        try {
            val readers = (1..readerCount).map {
                pool.submit {
                    ready.countDown()
                    check(resume.await(5, TimeUnit.SECONDS)) { "the test did not release the readers" }
                    repeat(readsPerReader) {
                        if (source.nextFrame() != null) {
                            framesYielded.incrementAndGet()
                        }
                        reads.incrementAndGet()
                    }
                }
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS), "the readers did not reach the barrier")

            // When: close completes before any reader is allowed past the barrier.
            source.close()
            resume.countDown()
            readers.forEach { it.get(5, TimeUnit.SECONDS) }

            // Then: every read happens after close returned, so no frame can escape.
            assertEquals(readerCount * readsPerReader, reads.get())
            assertEquals(0, framesYielded.get(), "a frame escaped after close() returned")
        } finally {
            pool.shutdownNow()
        }
    }

    @Test
    fun `Given readers racing close When close returns Then every later nextFrame call returns null`() {
        // Given: four readers hammer the source while the test closes it.
        val source = OpusPacketFrameSource.fromResource()
        val readerCount = 4
        val started = CountDownLatch(readerCount)
        val stopped = AtomicBoolean(false)
        val framesYielded = AtomicInteger(0)
        val violations = AtomicInteger(0)
        val pool = Executors.newFixedThreadPool(readerCount)

        try {
            val readers = (1..readerCount).map {
                pool.submit {
                    started.countDown()
                    while (!stopped.get() && !Thread.currentThread().isInterrupted) {
                        if (source.nextFrame() != null) {
                            framesYielded.incrementAndGet()
                        }
                    }
                    // The test sets stopped only after close() returned, so this call is
                    // strictly ordered after close and must observe the closed flag.
                    if (source.nextFrame() != null) {
                        violations.incrementAndGet()
                    }
                }
            }
            assertTrue(started.await(5, TimeUnit.SECONDS), "the readers did not start")

            // When
            source.close()
            stopped.set(true)
            readers.forEach { it.get(5, TimeUnit.SECONDS) }

            // Then
            assertEquals(0, violations.get(), "a frame was yielded after close() returned")
            assertTrue(
                framesYielded.get() <= source.expectedFrameCount,
                "the source handed out ${framesYielded.get()} frames for " +
                    "${source.expectedFrameCount} packets",
            )
        } finally {
            stopped.set(true)
            pool.shutdownNow()
        }
    }

    /**
     * Duration derived from the Opus TOC byte, mirroring the generator's validator so the
     * committed asset is checked independently of container metadata.
     */
    private fun opusPacketDurationMillis(packet: ByteArray): Double {
        val toc = packet[0].toInt() and 0xFF
        val config = toc shr 3
        val code = toc and 0x03

        val frameMicros = when {
            config < 12 -> intArrayOf(10_000, 20_000, 40_000, 60_000)[config and 0x03]
            config < 16 -> if (config == 12 || config == 14) 10_000 else 20_000
            else -> intArrayOf(2_500, 5_000, 10_000, 20_000)[config and 0x03]
        }

        val frameCount = when (code) {
            0 -> 1

            1, 2 -> 2

            else -> {
                assertTrue(packet.size >= 2, "a code-3 packet needs a frame count byte")
                packet[1].toInt() and 0x3F
            }
        }

        assertTrue(frameCount >= 1, "a code-3 packet must declare at least one frame")
        return frameCount * frameMicros / 1_000.0
    }

    private fun lengthPrefixed(packet: ByteArray): ByteArray = ByteArrayOutputStream().use { output ->
        output.write((packet.size shr 8) and 0xFF)
        output.write(packet.size and 0xFF)
        output.write(packet)
        output.toByteArray()
    }

    private fun loaderFor(resource: ByteArray): ClassLoader =
        BytesClassLoader(mapOf("malformed.opuspackets" to resource))

    private class BytesClassLoader(
        private val resources: Map<String, ByteArray>,
    ) : ClassLoader() {
        override fun getResourceAsStream(name: String): InputStream? =
            resources[name]?.inputStream()
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
