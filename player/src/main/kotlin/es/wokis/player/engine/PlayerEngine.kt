package es.wokis.player.engine

import es.wokis.player.engine.audio.ContinuousPlayerAudioStream
import es.wokis.player.engine.event.PlayerEvent
import es.wokis.player.engine.model.PlayerSnapshot
import es.wokis.player.engine.model.PlayerTrack
import kotlinx.coroutines.flow.Flow

/**
 * Gateway-neutral, per-guild player engine.
 *
 * All transitions are serialized through an internal mailbox/actor: public suspend commands enqueue
 * one command and the actor applies it in order; Lavaplayer callbacks only enqueue, so no audio
 * thread mutates the queue or blocks on presentation. No gateway or Lavaplayer type appears in the
 * contract, and implementations must not expose Lavaplayer `AudioTrack` publicly.
 */
interface PlayerEngine : AutoCloseable {

    /**
     * Ordered, intentionally **single-subscriber** event stream: the gateway adapter exclusively
     * owns collection. A second collector throws instead of load-balancing events away; UI
     * observers consume adapter presentation state, and fan-out belongs at the adapter layer.
     */
    val events: Flow<PlayerEvent>

    /** Loads and queues [url]; [addToFront] mirrors the existing `/next` behaviour. */
    suspend fun loadAndPlay(url: String, addToFront: Boolean = false)

    /** Loads several inputs in caller order. */
    suspend fun loadAndPlayMultiple(tracks: List<String>)

    /** Loads several inputs in caller order, applying [customName] to each loaded track. */
    suspend fun loadAndPlayMultipleWithCustomName(tracks: List<String>, customName: String)

    /** Loads a Kokoro TTS input synchronously and queues the resulting track. */
    suspend fun loadAndPlayTts(message: String)

    /** Loads a radio stream synchronously, applying the radio presentation overrides. */
    suspend fun playRadio(radioName: String, radioUrl: String, customFavicon: String)

    /** Pauses after the mailbox acknowledges the transition; the track position stops advancing. */
    suspend fun pause(): Boolean

    /** Resumes the same track at the position it had when [pause] was acknowledged. */
    suspend fun resume(): Boolean

    /** Stops the current track and advances the queue. A paused engine stays paused. */
    suspend fun skip(): Boolean

    /** Clears the queue, stops the current track and marks the stream terminal. */
    suspend fun stop(): Boolean

    suspend fun shuffle(): Boolean

    /** Moves a matching pending track to the front, or returns `null` when no track matches. */
    suspend fun moveTrackToNext(searchTerm: String): PlayerTrack?

    fun snapshot(): PlayerSnapshot

    fun isPaused(): Boolean

    fun isQueueEmpty(): Boolean

    /**
     * Creates a long-lived non-blocking audio stream over this engine's player.
     *
     * The caller owns the returned stream and must close it when the voice connection ends.
     */
    fun audioStream(): ContinuousPlayerAudioStream

    override fun close()
}
