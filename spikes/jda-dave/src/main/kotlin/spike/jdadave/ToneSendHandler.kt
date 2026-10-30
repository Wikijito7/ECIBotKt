package spike.jdadave

import net.dv8tion.jda.api.audio.AudioSendHandler
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Sends one pre-rendered 20 ms PCM frame per JDA send cycle.
 *
 * JDA requires 48 kHz, 16-bit, stereo, signed **big-endian** PCM when [isOpus] is
 * false. Every call to [provide20MsAudio] returns exactly one 20 ms frame (3840 bytes)
 * and advances the cursor once. The frames are rendered up front, so the callback does
 * no I/O and cannot block JDA's audio send thread.
 *
 * [framesProvided] counts frames **handed to JDA's send loop**. It is not confirmation
 * that the frames were encoded, encrypted, transmitted or heard: only a second account
 * hearing the tone proves audible delivery.
 */
class ToneSendHandler(
    val frequencyHz: Double = 440.0,
    val durationMillis: Int = 1_500,
    val amplitude: Double = 0.10,
) : AudioSendHandler {

    init {
        require(durationMillis > 0) { "durationMillis must be positive" }
        require(durationMillis % FRAME_MILLIS == 0) {
            "durationMillis must be a multiple of $FRAME_MILLIS ms"
        }
        require(amplitude in 0.0..1.0) { "amplitude must be in [0.0, 1.0]" }
    }

    private val renderedFrames: Array<ByteArray> = renderFrames()
    private var nextFrameIndex = 0
    private val framesProvidedCounter = AtomicInteger(0)
    private val finished = CompletableFuture<Unit>()

    val totalFrames: Int get() = renderedFrames.size

    /** Frames returned to JDA so far; not a confirmed network transmission count. */
    val framesProvided: Int get() = framesProvidedCounter.get()

    override fun canProvide(): Boolean = nextFrameIndex < renderedFrames.size

    override fun provide20MsAudio(): ByteBuffer? {
        val index = nextFrameIndex
        if (index >= renderedFrames.size) {
            return null
        }

        nextFrameIndex = index + 1
        framesProvidedCounter.set(nextFrameIndex)
        if (nextFrameIndex == renderedFrames.size) {
            finished.complete(Unit)
        }
        return ByteBuffer.wrap(renderedFrames[index])
    }

    /** PCM, so JDA encodes it to Opus itself before DAVE encryption. */
    override fun isOpus(): Boolean = false

    fun awaitFinished(timeout: Long, unit: TimeUnit) {
        finished.get(timeout, unit)
    }

    private fun renderFrames(): Array<ByteArray> {
        val samplesPerFrame = SAMPLE_RATE * FRAME_MILLIS / 1_000
        val totalSamples = SAMPLE_RATE * durationMillis / 1_000
        val frameCount = durationMillis / FRAME_MILLIS
        val fadeSamples = SAMPLE_RATE * FADE_MILLIS / 1_000
        val frames = Array(frameCount) { ByteArray(FRAME_BYTES) }

        for (frameIndex in 0 until frameCount) {
            val frame = ByteBuffer.wrap(frames[frameIndex]).order(ByteOrder.BIG_ENDIAN)
            for (sampleInFrame in 0 until samplesPerFrame) {
                val sampleIndex = frameIndex * samplesPerFrame + sampleInFrame
                val envelope = when {
                    sampleIndex < fadeSamples -> sampleIndex.toDouble() / fadeSamples
                    sampleIndex >= totalSamples - fadeSamples ->
                        (totalSamples - 1 - sampleIndex).toDouble() / fadeSamples
                    else -> 1.0
                }
                val seconds = sampleIndex.toDouble() / SAMPLE_RATE
                val value = (
                    sin(2.0 * PI * frequencyHz * seconds) * amplitude * envelope * Short.MAX_VALUE
                    ).roundToInt().toShort()

                frame.putShort(value) // left
                frame.putShort(value) // right
            }
        }
        return frames
    }

    companion object {
        const val SAMPLE_RATE = 48_000
        const val CHANNELS = 2
        const val BITS_PER_SAMPLE = 16
        const val FRAME_MILLIS = 20
        const val FADE_MILLIS = 10
        const val FRAME_BYTES = SAMPLE_RATE * FRAME_MILLIS / 1_000 * CHANNELS * BITS_PER_SAMPLE / 8
    }
}
