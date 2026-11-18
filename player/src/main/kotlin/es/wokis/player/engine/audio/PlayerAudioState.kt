package es.wokis.player.engine.audio

import es.wokis.voice.AudioFrame

/**
 * Gateway-neutral continuous player-audio contract: a long-lived stream that spans tracks and can
 * be paused.
 *
 * [PlayerAudioState.Frame] is one pre-encoded 20 ms Opus packet. [PlayerAudioState.PausedSilence]
 * means paused or momentarily unbuffered while active: adapters must send 20 ms of valid Opus
 * silence, not EOF, and must not advance the track position. [PlayerAudioState.Ended] is the only
 * terminal state, used for a stop or a finished queue.
 *
 * [next] is non-blocking and performs no I/O. It polls Lavaplayer's `provide(0)`: while paused
 * Lavaplayer returns `null` but refreshes its cleanup liveness clock, so the audio path must keep
 * calling it instead of stopping the producer.
 */
interface ContinuousPlayerAudioStream : AutoCloseable {

    fun next(): PlayerAudioState

    /** Idempotent; after it, [next] returns [PlayerAudioState.Ended]. */
    override fun close()
}

sealed interface PlayerAudioState {

    /** A real 20 ms Opus frame from the player. */
    data class Frame(val frame: AudioFrame) : PlayerAudioState

    /** 20 ms of Opus silence: paused playback, or a momentary gap while a track is active. */
    data object PausedSilence : PlayerAudioState

    /** Terminal: the playback stopped or the queue finished. */
    data object Ended : PlayerAudioState
}
