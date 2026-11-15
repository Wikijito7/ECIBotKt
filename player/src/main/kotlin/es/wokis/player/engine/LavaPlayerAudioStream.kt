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
 * Every call polls `provide(0)`, including while paused. Lavaplayer treats a zero-timeout poll
 * during pause as "no frame" but still refreshes its last-request clock, which keeps the player
 * from being cleaned up after the default ~60 s idle threshold. The audio send thread therefore
 * never blocks on audio data and never calls `provide(timeout > 0)`.
 *
 * A paused player, or a momentarily unbuffered active player, maps to
 * [PlayerAudioState.PausedSilence]. A provider exception or a malformed provider packet is **not**
 * silence: it is reported once through [onFailure] and the stream becomes terminal
 * ([PlayerAudioState.Ended]), so the engine can advance its queue or report the failure instead of
 * hiding a broken provider forever.
 *
 * [onFailure] is invoked on the audio thread. Implementations must only enqueue (for example into
 * the engine mailbox) and must never block or perform I/O.
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
