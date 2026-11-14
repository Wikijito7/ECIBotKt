package es.wokis.voice

import java.nio.ByteBuffer

/**
 * One outgoing audio frame.
 *
 * The frame owns a private copy of its payload, so callers may reuse or mutate their source
 * array after construction. The frame never hands out a mutable alias to that stored data:
 * [payloadCopy] and [payloadBuffer] both return defensive copies.
 *
 * Construction validates the encoded payload against its [AudioFrameFormat]: the bytes must be
 * one well-formed Opus packet whose TOC-declared duration matches [AudioFrameFormat.frameSamples].
 * A payload that merely fits in [MAX_OPUS_PACKET_BYTES] is not enough, so a malformed or
 * wrong-duration packet can never reach a gateway send loop.
 *
 * [payloadBuffer] allocates one array-backed heap buffer per call. For the bounded Opus packet
 * size this is a small, non-blocking allocation that a gateway send thread can consume safely.
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

    /**
     * A fresh array-backed buffer over a defensive copy of the encoded payload.
     *
     * The buffer owns its backing array, so mutating it (or its [ByteBuffer.array]) cannot
     * corrupt this frame or any other consumer. Each call returns an independent buffer
     * positioned at the start, which keeps retries safe.
     */
    fun payloadBuffer(): ByteBuffer = ByteBuffer.wrap(payload.copyOf())

    companion object {
        /**
         * Maximum size of one Opus packet (RFC 6716), used to reject malformed resources
         * before they reach the gateway send loop.
         */
        const val MAX_OPUS_PACKET_BYTES: Int = OpusPacketValidator.MAX_PACKET_BYTES

        /**
         * Creates a frame of [format] from an encoded [payload].
         *
         * @throws IllegalArgumentException when the payload is not a well-formed Opus packet,
         * when it carries a duration other than [AudioFrameFormat.frameSamples], or when the
         * format is not a supported Opus format.
         */
        fun of(format: AudioFrameFormat, payload: ByteArray): AudioFrame {
            require(format.isOpus) { "Only Opus frame formats are supported by the voice ports" }
            OpusPacketValidator.validate(payload, format.frameSamples)
            return AudioFrame(format, payload)
        }
    }
}
