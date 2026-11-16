package es.wokis.voice

import java.nio.ByteBuffer

/**
 * One outgoing audio frame.
 *
 * The frame owns a private copy of its payload; [payloadCopy] and [payloadBuffer] return defensive
 * copies, so callers may reuse their source array and consumers cannot corrupt the frame.
 *
 * Construction validates the payload as one well-formed Opus packet whose TOC-declared duration
 * matches [AudioFrameFormat.frameSamples]. Fitting in [MAX_OPUS_PACKET_BYTES] is not enough, so a
 * malformed or wrong-duration packet never reaches a gateway send loop.
 */
class AudioFrame private constructor(
    val format: AudioFrameFormat,
    payloadBytes: ByteArray,
) {

    private val payload: ByteArray = payloadBytes.copyOf()

    /** Encoded payload size in bytes. */
    val size: Int get() = payload.size

    /** Defensive copy of the encoded payload, useful for diagnostics and tests. */
    fun payloadCopy(): ByteArray = payload.copyOf()

    /** Fresh array-backed buffer over a defensive copy, positioned at 0 for each call. */
    fun payloadBuffer(): ByteBuffer = ByteBuffer.wrap(payload.copyOf())

    companion object {
        /** Maximum Opus packet size (RFC 6716). */
        const val MAX_OPUS_PACKET_BYTES: Int = OpusPacketValidator.MAX_PACKET_BYTES

        /**
         * Creates a frame of [format] from an encoded [payload].
         *
         * @throws IllegalArgumentException when the payload is malformed, carries a different
         * duration than [AudioFrameFormat.frameSamples], or the format is not Opus.
         */
        fun of(format: AudioFrameFormat, payload: ByteArray): AudioFrame {
            require(format.isOpus) { "Only Opus frame formats are supported by the voice ports" }
            OpusPacketValidator.validate(payload, format.frameSamples)
            return AudioFrame(format, payload)
        }
    }
}
