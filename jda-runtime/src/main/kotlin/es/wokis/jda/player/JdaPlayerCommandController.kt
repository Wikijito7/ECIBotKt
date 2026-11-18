package es.wokis.jda.player

import es.wokis.commands.neutral.CommandContext
import es.wokis.commands.player.PlayerCommandDefinitions
import es.wokis.domain.model.GuildId
import es.wokis.jda.command.toJdaId
import es.wokis.localization.GuildLocalizationService
import es.wokis.localization.PlayerMessageKeys
import es.wokis.message.ComponentId
import es.wokis.player.engine.PlayerEngine
import es.wokis.player.engine.model.PlayerSnapshot
import kotlinx.coroutines.CancellationException
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel
import org.slf4j.LoggerFactory

/**
 * Orchestrates `/play` and `/player`.
 *
 * `/play` hands the load to the guild's message coordinator, which joins voice once the adapter
 * reports an active track and publishes event-driven chat messages; every reply resolves through the
 * guild locale.
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
        if (action == null && rawAction.isNotBlank()) {
            return message(context, PlayerMessageKeys.PLAYER_UNKNOWN_ACTION, JdaPlayerAction.names.joinToString(", "))
        }
        val guild = context.guildId?.let { guildId -> jda.getGuildById(guildId.toJdaId()) }
            ?: return message(context, PlayerMessageKeys.ERROR_NO_GUILD)
        return when (action) {
            // Kord parity: no action only sets the player view up; playback state is untouched.
            null -> showPlayer(context, guild)
            JdaPlayerAction.QUEUE -> describeQueue(context, runtime.engineFor(guild).snapshot())
            JdaPlayerAction.PAUSE -> pause(context, runtime.engineFor(guild))
            JdaPlayerAction.RESUME -> resume(context, runtime.engineFor(guild))
            JdaPlayerAction.SKIP -> skip(context, runtime.engineFor(guild))
            JdaPlayerAction.STOP -> stop(context, runtime.engineFor(guild))
            JdaPlayerAction.DISCONNECT -> disconnect(context, guild, runtime.engineFor(guild))
            JdaPlayerAction.RECONNECT -> reconnect(context, guild)
        }
    }

    /**
     * Applies one player button with the same transport semantics as `/player`; non-player ids are
     * ignored so a stale row never mutates playback.
     */
    suspend fun applyButton(context: CommandContext, componentId: ComponentId) {
        val guild = context.guildId?.let { guildId -> jda.getGuildById(guildId.toJdaId()) } ?: return
        when (componentId) {
            ComponentId.PLAYER_PAUSE -> pause(context, runtime.engineFor(guild))
            ComponentId.PLAYER_RESUME -> resume(context, runtime.engineFor(guild))
            ComponentId.PLAYER_SKIP -> skip(context, runtime.engineFor(guild))
            ComponentId.PLAYER_SHUFFLE -> runtime.engineFor(guild).shuffle()
            ComponentId.PLAYER_DISCONNECT -> disconnect(context, guild, runtime.engineFor(guild))
            ComponentId.PLAYER_RECONNECT -> reconnect(context, guild)
            else -> Unit
        }
    }

    /** Kord parity: `/player` without an action only sets the player view up. */
    private suspend fun showPlayer(context: CommandContext, guild: Guild): String {
        val coordinator = runtime.messagesFor(guild)
        coordinator.recordCommand(
            channelId = context.channelId,
            guildId = GuildId(guild.idLong.toULong()),
            guildLocale = context.guildLocale,
            channelName = callerVoiceChannel(guild, context)?.name,
        )
        return coordinator.ensurePlayerView()
    }

    private suspend fun startPlayback(
        context: CommandContext,
        target: JdaPlayerVoiceTarget.Ready,
        input: String,
    ): String {
        // Kord parity: the guild comes from the invocation instead of a second JDA guild lookup.
        val guildId = context.guildId ?: return message(context, PlayerMessageKeys.ERROR_NO_GUILD)
        runtime.messagesFor(target.guild).let { coordinator ->
            coordinator.recordCommand(
                channelId = context.channelId,
                guildId = guildId,
                guildLocale = context.guildLocale,
                channelName = target.channel.name,
            )
            coordinator.startPlayback(target.channel, input)
        }
        // Kord parity: acknowledge the search immediately; events and failures arrive as chat messages.
        return message(context, PlayerMessageKeys.SEARCHING_SONG)
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

        private const val MAX_LISTED_TRACKS = 10
    }
}
