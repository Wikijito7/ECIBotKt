package es.wokis.player.engine.audio

import es.wokis.voice.AudioFrame

/**
 * Gateway-neutral continuous player-audio contract.
 *
 * Unlike the Phase 2C finite `AudioFrameSource`, this contract describes a long-lived player
 * stream that can span several tracks and can be paused:
 *
 * - [PlayerAudioState.Frame] is one pre-encoded 20 ms Opus packet to hand to the gateway.
 * - [PlayerAudioState.PausedSilence] means the player is paused (or momentarily has no buffered
 *   frame while still active). Adapters must send 20 ms of valid Opus silence; they must not
 *   treat it as EOF and must not advance the track position.
 * - [PlayerAudioState.Ended] means the playback was stopped or the queue finished. It is the only
 *   terminal state.
 *
 * [next] is non-blocking and performs no I/O. The implementation polls Lavaplayer's `provide(0)`:
 * while paused Lavaplayer returns `null` but refreshes its cleanup liveness clock, which is why the
 * audio path must keep calling it instead of stopping the producer.
 */
interface ContinuousPlayerAudioStream : AutoCloseable {

    /** Returns the next state without blocking. */
    fun next(): PlayerAudioState

    /** Idempotent; after it, [next] returns [PlayerAudioState.Ended]. */
    override fun close()
}

/** One non-blocking step of a [ContinuousPlayerAudioStream]. */
sealed interface PlayerAudioState {

    /** A real 20 ms Opus frame from the player. */
    data class Frame(val frame: AudioFrame) : PlayerAudioState

    /** 20 ms of Opus silence: paused playback, or a momentary gap while a track is active. */
    data object PausedSilence : PlayerAudioState

    /** Terminal: the playback stopped or the queue finished. */
    data object Ended : PlayerAudioState
}
