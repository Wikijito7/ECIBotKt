package es.wokis.jda.player

import es.wokis.commands.neutral.CommandContext
import es.wokis.commands.player.PlayerCommandDefinitions
import es.wokis.domain.model.UserId
import es.wokis.jda.command.toJdaId
import es.wokis.localization.GuildLocalizationService
import es.wokis.localization.PlayerMessageKeys
import es.wokis.player.engine.PlayerEngine
import es.wokis.player.engine.model.PlayerSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel
import org.slf4j.LoggerFactory

/**
 * Orchestrates `/play` and `/player`. Load happens before connect, so voice is joined only once the
 * adapter reports an active track, and every reply resolves through the guild locale.
 */
internal class JdaPlayerCommandController(
    private val jda: JDA,
    private val runtime: JdaPlayerRuntime,
    private val localizationService: GuildLocalizationService,
) {

    suspend fun play(context: CommandContext, rawInput: String): String {
        val input = rawInput.trim()
        if (input.isEmpty()) {
            return message(
                context,
                PlayerMessageKeys.ERROR_NO_CONTENT_PROVIDED,
                PlayerCommandDefinitions.PLAY_OPTION_INPUT,
            )
        }
        return when (val target = resolveTarget(context)) {
            is JdaPlayerVoiceTarget.Unavailable -> message(context, target.reason.key)
            is JdaPlayerVoiceTarget.Ready -> startPlayback(context, target, input)
        }
    }

    suspend fun control(context: CommandContext, rawAction: String): String {
        val action = JdaPlayerAction.parse(rawAction)
            ?: return message(context, PlayerMessageKeys.PLAYER_UNKNOWN_ACTION, JdaPlayerAction.names.joinToString(", "))
        val guild = context.guildId?.let { guildId -> jda.getGuildById(guildId.toJdaId()) }
            ?: return message(context, PlayerMessageKeys.ERROR_NO_GUILD)
        val engine = runtime.engineFor(guild)
        return when (action) {
            JdaPlayerAction.QUEUE -> describeQueue(context, engine.snapshot())
            JdaPlayerAction.PAUSE -> pause(context, engine)
            JdaPlayerAction.RESUME -> resume(context, engine)
            JdaPlayerAction.SKIP -> skip(context, engine)
            JdaPlayerAction.STOP -> stop(context, engine)
            JdaPlayerAction.DISCONNECT -> disconnect(context, guild, engine)
            JdaPlayerAction.RECONNECT -> reconnect(context, guild)
        }
    }

    private suspend fun startPlayback(
        context: CommandContext,
        target: JdaPlayerVoiceTarget.Ready,
        input: String,
    ): String {
        val engine = runtime.engineFor(target.guild)
        val adapter = runtime.voiceAdapterFor(target.guild)
        val baseRevision = adapter.presentation.value.revision

        engine.loadAndPlay(input)

        return when (val start = awaitPlaybackStart(adapter, baseRevision)) {
            JdaPlayerPlaybackStart.TimedOut -> message(context, PlayerMessageKeys.PLAYER_LOAD_TIMEOUT)
            is JdaPlayerPlaybackStart.Failed -> loadFailureMessage(context, start.failure, input)
            JdaPlayerPlaybackStart.Started -> connectMessage(context, target) {
                message(context, PlayerMessageKeys.PLAYER_PLAY_CONNECTED)
            }
        }
    }

    private suspend fun loadFailureMessage(
        context: CommandContext,
        failure: JdaPlayerLoadFailure,
        input: String,
    ): String = when (failure) {
        JdaPlayerLoadFailure.NO_MATCHES -> message(context, PlayerMessageKeys.NO_MATCHES, input)
        JdaPlayerLoadFailure.LOAD_REJECTED -> message(context, PlayerMessageKeys.PLAYER_LOAD_REJECTED)
        JdaPlayerLoadFailure.PLAYBACK_FAILED -> message(context, PlayerMessageKeys.PLAYER_PLAYBACK_FAILED)
    }

    private suspend fun awaitPlaybackStart(
        adapter: JdaGuildPlayerVoiceAdapter,
        baseRevision: Long,
    ): JdaPlayerPlaybackStart {
        val state = withTimeoutOrNull(LOAD_START_TIMEOUT_MILLIS) {
            adapter.presentation.first { presentation ->
                presentation.revision > baseRevision &&
                    (presentation.activeTrack || presentation.loadFailure != null)
            }
        }
        if (state == null) return JdaPlayerPlaybackStart.TimedOut
        val failure = state.loadFailure
        return if (failure == null) JdaPlayerPlaybackStart.Started else JdaPlayerPlaybackStart.Failed(failure)
    }

    private suspend fun connectMessage(
        context: CommandContext,
        target: JdaPlayerVoiceTarget.Ready,
        onConnected: suspend () -> String,
    ): String = try {
        if (runtime.connect(target.guild, target.channel)) {
            onConnected()
        } else {
            message(context, PlayerMessageKeys.PLAYER_VOICE_SESSION_REFUSED)
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Exception) {
        LOG.warn("The player voice connection failed ({})", error.javaClass.simpleName)
        message(context, PlayerMessageKeys.PLAYER_VOICE_CONNECTION_FAILED)
    }

    private suspend fun disconnect(context: CommandContext, guild: Guild, engine: PlayerEngine): String {
        engine.stop()
        runtime.disconnect(guild)
        return message(context, PlayerMessageKeys.DISCONNECT_COMMAND_RESPONSE)
    }

    private suspend fun reconnect(context: CommandContext, guild: Guild): String {
        val channel = callerVoiceChannel(guild, context)
            ?: return message(context, PlayerMessageKeys.ERROR_NO_VOICE_CHANNEL)
        val result = try {
            runtime.reconnect(guild, channel)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            LOG.warn("The player voice reconnect failed ({})", error.javaClass.simpleName)
            return message(context, PlayerMessageKeys.PLAYER_RECONNECT_FAILED)
        }
        return when (result) {
            JdaPlayerReconnectResult.Reconnected ->
                if (runtime.engineFor(guild).snapshot().hasActiveTrack) {
                    message(context, PlayerMessageKeys.PLAYER_RECONNECT_PRESERVED)
                } else {
                    message(context, PlayerMessageKeys.PLAYER_RECONNECT_IDLE)
                }

            JdaPlayerReconnectResult.NotConnected -> message(context, PlayerMessageKeys.RECONNECT_NOT_CONNECTED)
            JdaPlayerReconnectResult.ForeignSession ->
                message(context, PlayerMessageKeys.PLAYER_RECONNECT_FOREIGN_SESSION)
            JdaPlayerReconnectResult.InProgress -> message(context, PlayerMessageKeys.PLAYER_RECONNECT_IN_PROGRESS)
            JdaPlayerReconnectResult.TimedOut -> message(context, PlayerMessageKeys.PLAYER_RECONNECT_TIMEOUT)
        }
    }

    private suspend fun pause(context: CommandContext, engine: PlayerEngine): String {
        if (!engine.snapshot().hasActiveTrack) return message(context, PlayerMessageKeys.ERROR_NO_TRACK_PLAYING)
        return if (engine.pause()) {
            message(context, PlayerMessageKeys.PLAYER_PAUSED)
        } else {
            message(context, PlayerMessageKeys.PLAYER_PLAYBACK_STATE_UNCHANGED)
        }
    }

    private suspend fun resume(context: CommandContext, engine: PlayerEngine): String {
        if (!engine.snapshot().hasActiveTrack) return message(context, PlayerMessageKeys.ERROR_NO_TRACK_PLAYING)
        return if (engine.resume()) {
            message(context, PlayerMessageKeys.PLAYER_RESUMED)
        } else {
            message(context, PlayerMessageKeys.PLAYER_PLAYBACK_STATE_UNCHANGED)
        }
    }

    private suspend fun skip(context: CommandContext, engine: PlayerEngine): String =
        if (engine.skip()) {
            message(context, PlayerMessageKeys.SKIP_COMMAND_RESPONSE)
        } else {
            message(context, PlayerMessageKeys.ERROR_NO_TRACK_PLAYING)
        }

    private suspend fun stop(context: CommandContext, engine: PlayerEngine): String {
        engine.stop()
        return message(context, PlayerMessageKeys.PLAYER_STOPPED)
    }

    /** Queue details are rendered only here; provider failure text is never echoed. */
    private suspend fun describeQueue(context: CommandContext, snapshot: PlayerSnapshot): String {
        val current = snapshot.current ?: return message(context, PlayerMessageKeys.PLAYER_SERVER_QUEUE_EMPTY)
        val upcoming = snapshot.queue.take(MAX_LISTED_TRACKS).mapIndexed { index, track ->
            "${index + 1}. ${track.displayName}"
        }
        return buildString {
            append(message(context, PlayerMessageKeys.PLAYER_CURRENT_TRACK))
            append(": ").append(current.displayName)
            append(" | ").append(message(context, PlayerMessageKeys.PLAYER_SERVER_QUEUE))
            append(": ").append(snapshot.queue.size)
            if (upcoming.isNotEmpty()) {
                append('\n').append(upcoming.joinToString(separator = "\n"))
            }
            if (snapshot.queue.size > upcoming.size) {
                append('\n').append(
                    message(
                        context,
                        PlayerMessageKeys.PLAYER_QUEUE_TRACKS_REMAINING,
                        snapshot.queue.size - upcoming.size,
                    ),
                )
            }
        }
    }

    private suspend fun message(context: CommandContext, key: String, vararg arguments: Any): String =
        if (arguments.isEmpty()) {
            localizationService.getString(
                key = key,
                guildId = context.guildId,
                guildDefaultLocale = context.guildLocale,
            )
        } else {
            localizationService.getStringFormat(
                key = key,
                guildId = context.guildId,
                guildDefaultLocale = context.guildLocale,
                arguments = arguments,
            )
        }

    private fun resolveTarget(context: CommandContext): JdaPlayerVoiceTarget {
        val guild = context.guildId?.let { guildId -> jda.getGuildById(guildId.toJdaId()) }
            ?: return JdaPlayerVoiceTarget.Unavailable(JdaPlayerUnavailableReason.NO_GUILD)
        val channel = callerVoiceChannel(guild, context)
            ?: return JdaPlayerVoiceTarget.Unavailable(JdaPlayerUnavailableReason.NO_VOICE_CHANNEL)
        return JdaPlayerVoiceTarget.Ready(guild, channel)
    }

    private fun callerVoiceChannel(guild: Guild, context: CommandContext): VoiceChannel? =
        guild.getMemberById(context.userId.toJdaId())?.voiceState?.channel as? VoiceChannel

    private companion object {
        private val LOG = LoggerFactory.getLogger(JdaPlayerCommandController::class.java)

        private const val LOAD_START_TIMEOUT_MILLIS = 30_000L
        private const val MAX_LISTED_TRACKS = 10
    }
}

private fun UserId.toJdaId(): Long {
    require(value <= Long.MAX_VALUE.toULong()) {
        "User id does not fit in JDA's signed 64-bit id range"
    }
    return value.toLong()
}
