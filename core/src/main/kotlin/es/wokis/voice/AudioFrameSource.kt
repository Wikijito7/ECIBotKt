package es.wokis.voice

/**
 * Gateway-neutral source of outgoing audio frames.
 *
 * [nextFrame] is non-blocking and performs no I/O, so an adapter may call it from a gateway audio
 * thread; implementations must tolerate one consumer plus concurrent [close]. At most
 * [expectedFrameCount] frames are returned, then `null` forever. [close] is idempotent and makes
 * [nextFrame] return `null`; the owner must close the source on every exit path.
 */
interface AudioFrameSource : AutoCloseable {

    /** Format of every frame returned by this source. */
    val format: AudioFrameFormat

    /**
     * Frames a complete playback should supply to the adapter's send loop. A hand-off budget, not
     * confirmed network delivery: a matching count proves only that every frame was emitted.
     */
    val expectedFrameCount: Int

    /** Returns the next frame, or `null` when the source is exhausted or closed. */
    fun nextFrame(): AudioFrame?

    override fun close()
}
