package es.wokis.voice

/**
 * Minimal parser for the Opus packet framing defined by RFC 6716 section 3.2.
 *
 * The voice ports only carry pre-encoded packets, so an [AudioFrame] must not trust an
 * arbitrary byte array that merely fits in [MAX_PACKET_BYTES]: [AudioFrame] uses [validate]
 * to reject packets whose TOC framing is malformed, truncated or does not carry exactly the
 * duration of its format.
 *
 * The parser reads only the TOC byte, the frame-count byte and the frame-length headers. It
 * never decodes audio and depends on no audio library, so it stays gateway-neutral and cheap
 * enough to run while a packet source is loaded.
 *
 * Durations are expressed in decoded samples at the 48 kHz rate Opus always decodes to:
 * 2.5 ms = 120 samples, 5 ms = 240, 10 ms = 480, 20 ms = 960, 40 ms = 1920 and 60 ms = 2880.
 * A packet may legally carry zero-length frames (DTX), so an empty *frame* is accepted; an
 * empty *packet* is not, and neither is a code 3 packet that declares zero frames.
 */
object OpusPacketValidator {

    /** Maximum size of one Opus packet in bytes (RFC 6716 section 3.2.1). */
    const val MAX_PACKET_BYTES: Int = 1275

    /** Decoded samples in 20 ms at the 48 kHz rate Opus always decodes to. */
    const val TWENTY_MILLISECONDS_SAMPLES: Int = 960

    /**
     * Frame count and decoded duration declared by one packet's TOC and framing headers.
     *
     * @property frameCount number of frames the packet carries, always at least one.
     * @property durationSamples decoded duration at 48 kHz.
     */
    data class Framing(
        val frameCount: Int,
        val durationSamples: Int,
    )

    /**
     * Parses the framing of [payload] and returns its frame count and duration.
     *
     * @throws IllegalArgumentException when the packet is empty, exceeds [MAX_PACKET_BYTES],
     * has truncated or inconsistent framing headers, declares zero code 3 frames or claims
     * more than the RFC limit of 120 ms of audio.
     */
    fun parse(payload: ByteArray): Framing {
        require(payload.isNotEmpty()) { "An Opus packet payload cannot be empty" }
        require(payload.size <= MAX_PACKET_BYTES) {
            "An Opus packet cannot exceed $MAX_PACKET_BYTES bytes, got ${payload.size}"
        }

        val toc = payload[0].toInt() and 0xFF
        val samplesPerFrame = samplesPerFrame(toc ushr CONFIG_SHIFT)
        val frameCount = when (toc and CODE_MASK) {
            CODE_ONE_FRAME -> 1
            CODE_TWO_CBR_FRAMES -> {
                val frameBytes = payload.size - 1
                require(frameBytes % 2 == 0) {
                    "A code 1 Opus packet must split an even number of frame bytes, got $frameBytes"
                }
                2
            }
            CODE_TWO_VBR_FRAMES -> {
                val (firstLength, headerBytes) = readFrameLength(payload, 1, payload.size)
                val secondLength = payload.size - 1 - headerBytes - firstLength
                require(secondLength >= 0) {
                    "A code 2 Opus packet declares $firstLength first-frame bytes that do not fit " +
                        "the ${payload.size - 1 - headerBytes} remaining payload bytes"
                }
                2
            }
            else -> parseCodeThreeFrameCount(payload)
        }

        val durationSamples = frameCount * samplesPerFrame
        require(durationSamples <= MAX_PACKET_DURATION_SAMPLES) {
            "An Opus packet cannot carry more than 120 ms of audio, got $durationSamples samples"
        }
        return Framing(frameCount = frameCount, durationSamples = durationSamples)
    }

    /**
     * Parses [payload] and rejects it unless it carries exactly [expectedDurationSamples]
     * decoded samples at 48 kHz.
     *
     * @throws IllegalArgumentException when the packet is malformed or has a different duration.
     */
    fun validate(
        payload: ByteArray,
        expectedDurationSamples: Int = TWENTY_MILLISECONDS_SAMPLES,
    ): Framing {
        val framing = parse(payload)
        require(framing.durationSamples == expectedDurationSamples) {
            "An Opus packet must carry exactly $expectedDurationSamples samples at 48 kHz, " +
                "but carries ${framing.durationSamples}"
        }
        return framing
    }

    /**
     * Code 3 encodes the frame count in the second byte: bit 7 is VBR, bit 6 is padding and
     * bits 0-5 are the frame count (RFC 6716 section 3.2.5).
     */
    private fun parseCodeThreeFrameCount(payload: ByteArray): Int {
        require(payload.size >= 2) { "A code 3 Opus packet must contain a frame count byte" }
        val countByte = payload[1].toInt() and 0xFF
        val frameCount = countByte and FRAME_COUNT_MASK
        require(frameCount > 0) { "A code 3 Opus packet must declare at least one frame" }

        var index = 2
        var dataEnd = payload.size
        if (countByte and PADDING_FLAG != 0) {
            var paddingBytes = 0
            while (true) {
                require(index < payload.size) { "Truncated Opus padding length header" }
                val value = payload[index].toInt() and 0xFF
                index++
                if (value == MAX_PADDING_RUN) {
                    paddingBytes += MAX_PADDING_RUN - 1
                } else {
                    paddingBytes += value
                    break
                }
            }
            require(paddingBytes <= payload.size - index) {
                "Opus padding of $paddingBytes bytes does not fit the packet payload"
            }
            dataEnd = payload.size - paddingBytes
        }

        if (countByte and VBR_FLAG != 0) {
            var declaredBytes = 0
            repeat(frameCount - 1) {
                val (length, headerBytes) = readFrameLength(payload, index, dataEnd)
                declaredBytes += length
                index += headerBytes
                require(declaredBytes <= dataEnd - index) {
                    "The VBR frame lengths of a code 3 Opus packet exceed the packet payload"
                }
            }
        } else {
            val frameBytes = dataEnd - index
            require(frameBytes % frameCount == 0) {
                "A CBR code 3 Opus packet must split $frameBytes frame bytes across $frameCount frames"
            }
        }
        return frameCount
    }

    /**
     * Reads a one- or two-byte frame length header starting at [index] and bounded by [limit].
     * A leading byte in 252..255 extends the length to `(secondByte * 4) + firstByte`.
     */
    private fun readFrameLength(payload: ByteArray, index: Int, limit: Int): FrameLength {
        require(index < limit) { "Truncated Opus frame length header" }
        val first = payload[index].toInt() and 0xFF
        if (first < TWO_BYTE_LENGTH_MARKER) {
            return FrameLength(length = first, headerBytes = 1)
        }
        require(index + 1 < limit) { "Truncated two-byte Opus frame length header" }
        val second = payload[index + 1].toInt() and 0xFF
        return FrameLength(length = (second shl 2) + first, headerBytes = 2)
    }

    /** Decoded samples of one frame for the 5-bit TOC config (RFC 6716 table 2). */
    private fun samplesPerFrame(config: Int): Int = when {
        config < HYBRID_CONFIG_LIMIT -> when (config and 0x03) {
            0 -> SAMPLES_10_MS
            1 -> SAMPLES_20_MS
            2 -> SAMPLES_40_MS
            else -> SAMPLES_60_MS
        }
        config < CELT_CONFIG_LIMIT -> when (config and 0x01) {
            0 -> SAMPLES_10_MS
            else -> SAMPLES_20_MS
        }
        else -> when (config and 0x03) {
            0 -> SAMPLES_2_5_MS
            1 -> SAMPLES_5_MS
            2 -> SAMPLES_10_MS
            else -> SAMPLES_20_MS
        }
    }

    private data class FrameLength(
        val length: Int,
        val headerBytes: Int,
    )

    private const val CONFIG_SHIFT = 3
    private const val CODE_MASK = 0x03
    private const val CODE_ONE_FRAME = 0
    private const val CODE_TWO_CBR_FRAMES = 1
    private const val CODE_TWO_VBR_FRAMES = 2

    private const val VBR_FLAG = 0x80
    private const val PADDING_FLAG = 0x40
    private const val FRAME_COUNT_MASK = 0x3F

    private const val TWO_BYTE_LENGTH_MARKER = 252
    private const val MAX_PADDING_RUN = 255

    private const val HYBRID_CONFIG_LIMIT = 12
    private const val CELT_CONFIG_LIMIT = 16

    private const val SAMPLES_2_5_MS = 120
    private const val SAMPLES_5_MS = 240
    private const val SAMPLES_10_MS = 480
    private const val SAMPLES_20_MS = 960
    private const val SAMPLES_40_MS = 1920
    private const val SAMPLES_60_MS = 2880

    private const val MAX_PACKET_DURATION_SAMPLES = 120 * 48
}
