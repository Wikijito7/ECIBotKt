package es.wokis.commands.voicewalkthrough

import es.wokis.commands.neutral.CommandContext
import es.wokis.commands.neutral.CommandDefinition
import es.wokis.commands.neutral.CommandHandler
import es.wokis.commands.neutral.CommandResponder
import es.wokis.commands.neutral.CommandResponse
import es.wokis.commands.neutral.GuildCommandRegistrar
import es.wokis.domain.model.GuildId
import es.wokis.voice.AudioFrameSource
import es.wokis.voice.VoiceFailureReason
import es.wokis.voice.VoicePlaybackResult
import es.wokis.voice.VoiceRequestRejection
import es.wokis.voice.VoiceSessionPort
import kotlin.coroutines.cancellation.CancellationException

/**
 * Neutral diagnostic handler for the Phase 2C voice port.
 *
 * It defers ephemerally, asks [voiceSession] to play a short pre-encoded Opus tone into the
 * invoking user's regular voice channel, reports the frame count supplied to the adapter's send
 * loop (a hand-off count, not a confirmation of network delivery) and always closes the
 * caller-owned [AudioFrameSource]. Gateway/DAVE types stay in the adapter module: this
 * handler only knows the neutral ports.
 *
 * User-facing text never includes a guild, channel, user or application identifier.
 */
class JdaVoiceWalkthroughCommand(
    private val voiceSession: VoiceSessionPort,
    private val frameSourceFactory: () -> AudioFrameSource,
) : CommandHandler {

    override val name: String = COMMAND_NAME

    /** Gateway-neutral definition used by adapters to publish this command. */
    val definition: CommandDefinition = CommandDefinition(
        name = COMMAND_NAME,
        description = COMMAND_DESCRIPTION,
    )

    /** Publishes this command into [guildId] through [registrar]; there is no global variant. */
    suspend fun registerIn(registrar: GuildCommandRegistrar, guildId: GuildId) {
        registrar.register(guildId, definition)
    }

    override suspend fun handle(context: CommandContext, responder: CommandResponder) {
        responder.defer(ephemeral = true)

        val source = try {
            frameSourceFactory()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            responder.respond(CommandResponse(TONE_UNAVAILABLE_MESSAGE, ephemeral = true))
            return
        }

        try {
            val result = try {
                voiceSession.play(context, source)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                VoicePlaybackResult.Failed(VoiceFailureReason.INTERNAL_ERROR)
            }
            responder.respond(CommandResponse(result.toUserMessage(), ephemeral = true))
        } finally {
            source.close()
        }
    }

    private fun VoicePlaybackResult.toUserMessage(): String = when (this) {
        is VoicePlaybackResult.Completed ->
            "Voice walkthrough OK | handed $framesProvided/$expectedFrames frames to JDA's send loop " +
                "(delivery not confirmed)"

        is VoicePlaybackResult.Rejected -> when (reason) {
            VoiceRequestRejection.DIRECT_MESSAGE -> "This command must be used in a guild voice channel."

            VoiceRequestRejection.USER_NOT_IN_VOICE -> "Join a regular voice channel first, then try again."

            VoiceRequestRejection.STAGE_CHANNEL -> "Stage channels are not supported; use a regular voice channel."

            VoiceRequestRejection.UNSUPPORTED_CHANNEL -> "That channel type is not supported; use a regular voice channel."

            VoiceRequestRejection.OTHER_GUILD -> "Join a voice channel in this guild."

            VoiceRequestRejection.PLAYBACK_IN_PROGRESS -> "A voice walkthrough is already running. Wait for it to finish."

            VoiceRequestRejection.VOICE_SESSION_ALREADY_ACTIVE ->
                "The bot already has an active voice session in this guild. Wait for it to end and try again."
        }

        is VoicePlaybackResult.Failed -> when (reason) {
            VoiceFailureReason.MISSING_PERMISSION ->
                "The bot is missing permission to join or speak in that voice channel."

            VoiceFailureReason.CONNECT_TIMEOUT -> "Timed out waiting for the voice connection."

            VoiceFailureReason.CONNECTION_LOST -> "The voice connection was lost before playback completed."

            VoiceFailureReason.TONE_TIMEOUT -> "Timed out waiting for the tone to finish."

            VoiceFailureReason.INCOMPLETE_PLAYBACK -> "Not all tone frames were handed to the voice connection."

            VoiceFailureReason.DISCONNECT_UNCONFIRMED -> "The voice connection did not close cleanly."

            VoiceFailureReason.CLEANUP_FAILED ->
                "The voice session could not be cleaned up completely after the walkthrough."

            VoiceFailureReason.INTERNAL_ERROR -> "The voice walkthrough failed."
        }
    }

    companion object {
        const val COMMAND_NAME = "jda-voice-walkthrough"
        const val COMMAND_DESCRIPTION =
            "Diagnostic command: join your voice channel, play a short tone and disconnect"

        private const val TONE_UNAVAILABLE_MESSAGE = "The walkthrough tone could not be loaded."
    }
}
