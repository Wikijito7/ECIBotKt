package es.wokis.jda.voice

import es.wokis.voice.AudioFrame
import es.wokis.voice.AudioFrameFormat
import es.wokis.voice.AudioFrameSource
import java.io.InputStream
import java.nio.BufferUnderflowException
import java.nio.ByteBuffer

/**
 * In-memory [AudioFrameSource] over a length-prefixed Opus packet resource.
 *
 * Resource layout: a sequence of big-endian 16-bit lengths, each followed by exactly that
 * many bytes of one pre-encoded 20 ms Opus packet. There is no container metadata: every
 * packet is an independent Opus packet. The whole resource is parsed eagerly, so
 * [nextFrame] is a pure in-memory operation and is safe to call from JDA's audio thread.
 * Every packet is validated while loading: [AudioFrame.of] rejects a malformed Opus TOC or a
 * packet that does not carry exactly 20 ms, and the load fails with the packet index so a bad
 * resource never starts a run with corrupt audio.
 *
 * The cursor and the closed flag share one monitor, so [nextFrame] and [close] linearize:
 * either a frame is returned before [close] completes, or it is never returned at all. No
 * frame can escape after [close] has returned. The protected section only reads the already
 * parsed list — no I/O, logging or gateway callback runs under the lock — so JDA's audio
 * thread can never wait behind meaningful work.
 *
 * The resource is generated reproducibly by `tools/generate-walkthrough-tone.py`, which
 * validates every packet's 20 ms duration and the 1275-byte Opus packet limit before
 * writing it.
 */
internal class OpusPacketFrameSource private constructor(
    private val frames: List<AudioFrame>,
) : AudioFrameSource {

    private val lock = Any()
    private var cursor = 0
    private var closed = false

    override val format: AudioFrameFormat = AudioFrameFormat.OPUS_20MS

    override val expectedFrameCount: Int = frames.size

    override fun nextFrame(): AudioFrame? = synchronized(lock) {
        when {
            closed -> null
            cursor >= frames.size -> null
            else -> frames[cursor++]
        }
    }

    override fun close() {
        synchronized(lock) {
            closed = true
        }
    }

    companion object {
        const val WALKTHROUGH_TONE_RESOURCE = "/audio/walkthrough-tone-440.opuspackets"

        private const val LENGTH_PREFIX_BYTES = 2
        private const val MAX_PACKET_BYTES = AudioFrame.MAX_OPUS_PACKET_BYTES

        /**
         * Loads the packet source from [resource] on the classpath.
         *
         * @throws IllegalStateException when the resource is missing or malformed, so the
         * diagnostic command can report a clear failure instead of starting a silent run.
         */
        fun fromResource(
            resource: String = WALKTHROUGH_TONE_RESOURCE,
            loader: ClassLoader = OpusPacketFrameSource::class.java.classLoader,
        ): OpusPacketFrameSource {
            val stream = loader.getResourceAsStream(resource.removePrefix("/"))
                ?: throw IllegalStateException("Missing walkthrough tone resource: $resource")
            return stream.use { fromStream(it, resource) }
        }

        private fun fromStream(stream: InputStream, resource: String): OpusPacketFrameSource {
            val bytes = stream.readBytes()
            val buffer = ByteBuffer.wrap(bytes)
            val frames = ArrayList<AudioFrame>()

            try {
                while (buffer.remaining() > 0) {
                    if (buffer.remaining() < LENGTH_PREFIX_BYTES) {
                        throw IllegalStateException("Truncated packet length in $resource")
                    }
                    val length = buffer.short.toInt() and 0xFFFF
                    if (length !in 1..MAX_PACKET_BYTES) {
                        throw IllegalStateException("Invalid Opus packet length $length in $resource")
                    }
                    if (buffer.remaining() < length) {
                        throw IllegalStateException("Truncated Opus packet in $resource")
                    }
                    val payload = ByteArray(length)
                    buffer.get(payload)
                    frames += audioFrame(payload, frames.size + 1, resource)
                }
            } catch (error: BufferUnderflowException) {
                throw IllegalStateException("Truncated Opus packet resource: $resource", error)
            }

            if (frames.isEmpty()) {
                throw IllegalStateException("The walkthrough tone resource is empty: $resource")
            }
            return OpusPacketFrameSource(frames)
        }

        /**
         * Builds one frame and turns a malformed packet into a load failure that names the
         * packet, so a corrupt resource is reported instead of surfacing as a raw argument error.
         */
        private fun audioFrame(payload: ByteArray, packetNumber: Int, resource: String): AudioFrame =
            try {
                AudioFrame.of(AudioFrameFormat.OPUS_20MS, payload)
            } catch (error: IllegalArgumentException) {
                throw IllegalStateException(
                    "Invalid Opus packet $packetNumber in $resource: ${error.message}",
                    error,
                )
            }
    }
}
