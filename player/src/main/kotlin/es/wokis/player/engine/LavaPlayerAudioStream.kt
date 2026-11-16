package es.wokis.player.engine

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer
import es.wokis.player.engine.audio.ContinuousPlayerAudioStream
import es.wokis.player.engine.audio.PlayerAudioState
import es.wokis.voice.AudioFrame
import es.wokis.voice.AudioFrameFormat
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import com.sedmelluq.discord.lavaplayer.track.playback.AudioFrame as LavaAudioFrame

/**
 * Non-blocking [ContinuousPlayerAudioStream] over a Lavaplayer [AudioPlayer].
 *
 * Every call polls `provide(0)`, including while paused: Lavaplayer returns "no frame" but still
 * refreshes its cleanup liveness clock, keeping the player alive past the ~60 s idle threshold. The
 * send thread never blocks and never calls `provide(timeout > 0)`.
 *
 * Paused or momentarily unbuffered active playback maps to [PlayerAudioState.PausedSilence]. A
 * provider exception or malformed packet is **not** silence: it is reported once through
 * [onFailure] and the stream becomes [PlayerAudioState.Ended], so the engine advances or reports
 * instead of hiding a broken provider. [onFailure] runs on the audio thread and must only enqueue,
 * never block or perform I/O.
 */
internal class LavaPlayerAudioStream(
    private val player: AudioPlayer,
    private val isTerminated: () -> Boolean,
) : ContinuousPlayerAudioStream {

    /**
     * Set by the engine immediately after creation. Kept as a property (instead of a constructor
     * parameter) so the existing two-argument construction stays valid for focused tests.
     */
    var onFailure: ((Throwable) -> Unit)? = null

    private val lock = Any()
    private val failureReported = AtomicBoolean(false)
    private var closed = false

    override fun next(): PlayerAudioState {
        if (isClosed() || isTerminated()) return PlayerAudioState.Ended

        val provided: LavaAudioFrame? = try {
            player.provide(0, TimeUnit.MILLISECONDS)
        } catch (error: Exception) {
            return if (isTerminated()) PlayerAudioState.Ended else fail(error)
        }

        if (isClosed() || isTerminated()) return PlayerAudioState.Ended

        val paused = try {
            player.isPaused
        } catch (error: Exception) {
            return if (isTerminated()) PlayerAudioState.Ended else fail(error)
        }
        if (paused) return PlayerAudioState.PausedSilence

        val data = try {
            provided?.data
        } catch (error: Exception) {
            return if (isTerminated()) PlayerAudioState.Ended else fail(error)
        }
        if (data == null || data.isEmpty()) {
            // An active track can transiently have no buffered frame. Sending silence keeps the
            // real-time send loop alive without treating the gap as EOF.
            return PlayerAudioState.PausedSilence
        }

        return try {
            PlayerAudioState.Frame(AudioFrame.of(AudioFrameFormat.OPUS_20MS, data))
        } catch (error: Exception) {
            // A malformed provider packet must not be sent as Opus or hidden as silence forever.
            fail(error)
        }
    }

    override fun close() {
        synchronized(lock) {
            closed = true
        }
    }

    private fun fail(error: Throwable): PlayerAudioState {
        if (failureReported.compareAndSet(false, true)) {
            // The callback only enqueues for the engine, but the audio thread must never let any
            // observer exception escape from here.
            try {
                onFailure?.invoke(error)
            } catch (_: Throwable) {
                // The stream is already terminal; the engine records/advances independently.
            }
        }
        return PlayerAudioState.Ended
    }

    private fun isClosed(): Boolean = synchronized(lock) { closed }
}
