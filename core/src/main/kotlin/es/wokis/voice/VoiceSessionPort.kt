package es.wokis.voice

import es.wokis.commands.neutral.CommandContext

/**
 * Gateway-neutral voice-session port.
 *
 * The adapter resolves the invoking user's voice state and connects the bot to their
 * regular voice channel. Direct messages, users who are not in a voice channel, Stage
 * channels, unknown or unsupported audio channel types and channels in another guild are
 * rejected with a neutral [VoiceRequestRejection] instead of being treated as failures.
 *
 * Implementations must:
 * - keep at most one playback active at a time;
 * - reject with [VoiceRequestRejection.VOICE_SESSION_ALREADY_ACTIVE] when the gateway already
 *   owns a connected or connecting voice session in the target guild, instead of moving,
 *   replacing or closing that session;
 * - never close the caller-owned [AudioFrameSource] — the caller owns it;
 * - attempt to clear the send handler, close the audio connection and restore the audio-manager
 *   state on success, failure, timeout and cancellation, and report the outcome instead of
 *   assuming it succeeded: [VoiceFailureReason.DISCONNECT_UNCONFIRMED] when the disconnect was not
 *   confirmed and [VoiceFailureReason.CLEANUP_FAILED] when the disconnect was confirmed but
 *   another cleanup step failed. A failed cleanup is never reported as a clean success.
 */
interface VoiceSessionPort {

    /**
     * Plays [source] into the invoking user's current regular voice channel.
     *
     * Returns a [VoicePlaybackResult]; expected user errors are reported as
     * [VoicePlaybackResult.Rejected] and never thrown. Cancellation is not converted into a
     * result: the implementation attempts its bounded cleanup and rethrows it.
     */
    suspend fun play(context: CommandContext, source: AudioFrameSource): VoicePlaybackResult
}

/** Outcome of one [VoiceSessionPort.play] call. */
sealed interface VoicePlaybackResult {

    /**
     * Every expected frame was supplied to the adapter's audio send loop. This counts hand-off
     * only: it does not confirm that the gateway delivered the audio over the network.
     */
    data class Completed(
        val framesProvided: Int,
        val expectedFrames: Int,
    ) : VoicePlaybackResult

    /** The invocation cannot start a playback; the request is safe to show to the user. */
    data class Rejected(val reason: VoiceRequestRejection) : VoicePlaybackResult

    /** The playback started or was attempted, but did not complete successfully. */
    data class Failed(val reason: VoiceFailureReason) : VoicePlaybackResult
}

/** Expected, user-correctable reasons for not starting a playback. */
enum class VoiceRequestRejection {
    DIRECT_MESSAGE,
    USER_NOT_IN_VOICE,
    STAGE_CHANNEL,

    /** The user's current audio channel is of a type the voice port cannot target. */
    UNSUPPORTED_CHANNEL,
    OTHER_GUILD,
    PLAYBACK_IN_PROGRESS,

    /**
     * The gateway already owns a connected or connecting voice session in the target guild.
     * The adapter must not move, replace or close that existing session.
     */
    VOICE_SESSION_ALREADY_ACTIVE,
}

/** Neutral failure categories; adapters map gateway exceptions to these values. */
enum class VoiceFailureReason {
    MISSING_PERMISSION,
    CONNECT_TIMEOUT,
    CONNECTION_LOST,
    TONE_TIMEOUT,
    INCOMPLETE_PLAYBACK,

    /** The voice connection itself was not confirmed closed. */
    DISCONNECT_UNCONFIRMED,

    /**
     * The voice connection was confirmed closed, but another cleanup step failed, so the
     * session was not fully restored. Never reported as a clean success.
     */
    CLEANUP_FAILED,
    INTERNAL_ERROR,
}
