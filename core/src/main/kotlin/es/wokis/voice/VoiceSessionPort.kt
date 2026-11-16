package es.wokis.voice

import es.wokis.commands.neutral.CommandContext

/**
 * Gateway-neutral voice-session port.
 *
 * The adapter resolves the invoking user's voice state and connects the bot to their regular voice
 * channel. Direct messages, users not in voice, Stage channels, unsupported channel types and
 * channels in another guild are rejected with a neutral [VoiceRequestRejection], not a failure.
 *
 * Implementations must keep at most one playback active, reject an already connected or connecting
 * session in the target guild with [VoiceRequestRejection.VOICE_SESSION_ALREADY_ACTIVE] instead of
 * moving or closing it, never close the caller-owned [AudioFrameSource], and attempt cleanup on
 * every exit path. Cleanup failure is never a clean success: report
 * [VoiceFailureReason.DISCONNECT_UNCONFIRMED] when the disconnect was not confirmed and
 * [VoiceFailureReason.CLEANUP_FAILED] when it was but another step failed.
 */
interface VoiceSessionPort {

    /**
     * Plays [source] into the invoking user's current regular voice channel.
     *
     * Expected user errors are returned as [VoicePlaybackResult.Rejected], never thrown.
     * Cancellation is not converted into a result: cleanup is attempted and it is rethrown.
     */
    suspend fun play(context: CommandContext, source: AudioFrameSource): VoicePlaybackResult
}

/** Outcome of one [VoiceSessionPort.play] call. */
sealed interface VoicePlaybackResult {

    /** Every expected frame reached the adapter's send loop; hand-off only, not delivery. */
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

    /** A connected or connecting session already exists in the guild; do not take it over. */
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

    /** The connection closed, but another cleanup step failed; never a clean success. */
    CLEANUP_FAILED,
    INTERNAL_ERROR,
}
