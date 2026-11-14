package es.wokis.voice

/**
 * Gateway-neutral audio frame formats accepted by the voice ports.
 *
 * [OPUS_20MS] is a pre-encoded Opus packet that carries exactly 20 milliseconds of audio:
 * [frameSamples] decoded samples at the 48 kHz rate Opus always decodes to. The adapter must
 * not re-encode it; see [AudioFrame].
 */
enum class AudioFrameFormat(
    val frameDurationMillis: Int,
    val isOpus: Boolean,
    /** Decoded samples per frame at 48 kHz, validated against the packet's Opus TOC. */
    val frameSamples: Int,
) {
    OPUS_20MS(
        frameDurationMillis = 20,
        isOpus = true,
        frameSamples = OpusPacketValidator.TWENTY_MILLISECONDS_SAMPLES,
    ),
}
