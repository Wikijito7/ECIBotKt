package es.wokis.voice

/**
 * Gateway-neutral source of outgoing audio frames.
 *
 * Contract:
 * - [nextFrame] is non-blocking and performs no I/O; adapters may call it from a gateway
 *   audio thread. Implementations must be safe for one consumer plus concurrent [close].
 * - It returns at most [expectedFrameCount] frames and then `null` forever.
 * - [close] is idempotent; after it, [nextFrame] returns `null`. The owner must close the
 *   source on every exit path.
 */
interface AudioFrameSource : AutoCloseable {

    /** Format of every frame returned by this source. */
    val format: AudioFrameFormat

    /**
     * Number of frames a complete playback is expected to emit and supply to the adapter's send
     * loop.
     *
     * This is a hand-off budget, not a count of frames confirmed delivered over the network: the
     * source cannot observe the gateway's network layer, so a matching count proves only that
     * every expected frame was emitted to the adapter.
     */
    val expectedFrameCount: Int

    /** Returns the next frame, or `null` when the source is exhausted or closed. */
    fun nextFrame(): AudioFrame?

    override fun close()
}
