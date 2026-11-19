package es.wokis.commands.player

import es.wokis.commands.neutral.CommandContext
import es.wokis.localization.GuildLocalizationService
import es.wokis.localization.PlayerMessageKeys
import es.wokis.message.ComponentId
import es.wokis.player.engine.model.PlayerSnapshot
import kotlinx.coroutines.CancellationException
import org.slf4j.LoggerFactory

/**
 * Orchestrates `/play` and `/player`.
 *
 * `/play` hands the load to the guild's message coordinator, which joins voice once the adapter
 * reports an active track and publishes event-driven chat messages; every reply resolves through the
 * guild locale. All gateway lookups sit behind [actions], [voiceTargets] and [coordinators].
 */
class PlayerCommandController(
    private val actions: PlayerActionsPort,
    private val coordinators: PlayerMessageCoordinators,
    private val voiceTargets: PlayerVoiceTargetPort,
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
        return when (val target = voiceTargets.resolve(context)) {
            is PlayerVoiceTargetResult.Unavailable -> message(context, target.reason.key)
            is PlayerVoiceTargetResult.Ready -> startPlayback(context, target.target, input)
        }
    }

    suspend fun control(context: CommandContext, rawAction: String): String {
        val action = PlayerAction.parse(rawAction)
        if (action == null && rawAction.isNotBlank()) {
            return message(context, PlayerMessageKeys.PLAYER_UNKNOWN_ACTION, PlayerAction.names.joinToString(", "))
        }
        val actor = actor(context)
        if (actor == null || !actions.isGuildAvailable(actor)) {
            return message(context, PlayerMessageKeys.ERROR_NO_GUILD)
        }
        return when (action) {
            // Kord parity: no action only sets the player view up; playback state is untouched.
            null -> showPlayer(context, actor)
            PlayerAction.QUEUE -> describeQueue(context, actions.snapshot(actor))
            PlayerAction.PAUSE -> pause(context, actor)
            PlayerAction.RESUME -> resume(context, actor)
            PlayerAction.SKIP -> skip(context, actor)
            PlayerAction.STOP -> stop(context, actor)
            PlayerAction.DISCONNECT -> disconnect(context, actor)
            PlayerAction.RECONNECT -> reconnect(context, actor)
        }
    }

    /**
     * Applies one player button with the same transport semantics as `/player`; non-player ids are
     * ignored so a stale row never mutates playback.
     */
    suspend fun applyButton(context: CommandContext, componentId: ComponentId) {
        val actor = actor(context) ?: return
        if (!actions.isGuildAvailable(actor)) return
        when (componentId) {
            ComponentId.PLAYER_PAUSE -> pause(context, actor)
            ComponentId.PLAYER_RESUME -> resume(context, actor)
            ComponentId.PLAYER_SKIP -> skip(context, actor)
            ComponentId.PLAYER_SHUFFLE -> actions.shuffle(actor)
            ComponentId.PLAYER_DISCONNECT -> disconnect(context, actor)
            ComponentId.PLAYER_RECONNECT -> reconnect(context, actor)
            else -> Unit
        }
    }

    /** Kord parity: `/player` without an action only sets the player view up. */
    private suspend fun showPlayer(context: CommandContext, actor: PlayerActor): String {
        val coordinator = coordinators.coordinatorFor(actor.guildId)
            ?: return message(context, PlayerMessageKeys.ERROR_NO_GUILD)
        coordinator.recordCommand(
            channelId = context.channelId,
            guildId = actor.guildId,
            guildLocale = context.guildLocale,
            channelName = (voiceTargets.resolve(context) as? PlayerVoiceTargetResult.Ready)?.target?.channelName,
        )
        return coordinator.ensurePlayerView()
    }

    private suspend fun startPlayback(
        context: CommandContext,
        target: PlayerVoiceTarget,
        input: String,
    ): String {
        // Kord parity: the guild comes from the invocation instead of a second gateway lookup.
        val guildId = context.guildId ?: return message(context, PlayerMessageKeys.ERROR_NO_GUILD)
        val coordinator = coordinators.coordinatorFor(guildId)
            ?: return message(context, PlayerMessageKeys.ERROR_NO_GUILD)
        coordinator.recordCommand(
            channelId = context.channelId,
            guildId = guildId,
            guildLocale = context.guildLocale,
            channelName = target.channelName,
        )
        coordinator.startPlayback(target, input)
        // Kord parity: acknowledge the search immediately; events and failures arrive as chat messages.
        return message(context, PlayerMessageKeys.SEARCHING_SONG)
    }

    private suspend fun disconnect(context: CommandContext, actor: PlayerActor): String {
        actions.stop(actor)
        actions.disconnect(actor)
        return message(context, PlayerMessageKeys.DISCONNECT_COMMAND_RESPONSE)
    }

    private suspend fun reconnect(context: CommandContext, actor: PlayerActor): String {
        if (voiceTargets.resolve(context) !is PlayerVoiceTargetResult.Ready) {
            return message(context, PlayerMessageKeys.ERROR_NO_VOICE_CHANNEL)
        }
        val result = try {
            actions.reconnect(actor)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            LOG.warn("The player voice reconnect failed ({})", error.javaClass.simpleName)
            return message(context, PlayerMessageKeys.PLAYER_RECONNECT_FAILED)
        }
        return when (result) {
            PlayerReconnectResult.RECONNECTED ->
                if (actions.snapshot(actor)?.snapshot?.hasActiveTrack == true) {
                    message(context, PlayerMessageKeys.PLAYER_RECONNECT_PRESERVED)
                } else {
                    message(context, PlayerMessageKeys.PLAYER_RECONNECT_IDLE)
                }

            PlayerReconnectResult.NOT_CONNECTED -> message(context, PlayerMessageKeys.RECONNECT_NOT_CONNECTED)
            PlayerReconnectResult.FOREIGN_SESSION ->
                message(context, PlayerMessageKeys.PLAYER_RECONNECT_FOREIGN_SESSION)
            PlayerReconnectResult.IN_PROGRESS -> message(context, PlayerMessageKeys.PLAYER_RECONNECT_IN_PROGRESS)
            PlayerReconnectResult.TIMED_OUT -> message(context, PlayerMessageKeys.PLAYER_RECONNECT_TIMEOUT)
            PlayerReconnectResult.FAILED -> message(context, PlayerMessageKeys.PLAYER_RECONNECT_FAILED)
        }
    }

    private suspend fun pause(context: CommandContext, actor: PlayerActor): String {
        val snapshot = actions.snapshot(actor)?.snapshot
        if (snapshot == null) return message(context, PlayerMessageKeys.ERROR_NO_GUILD)
        if (!snapshot.hasActiveTrack) return message(context, PlayerMessageKeys.ERROR_NO_TRACK_PLAYING)
        return if (actions.pause(actor)) {
            message(context, PlayerMessageKeys.PLAYER_PAUSED)
        } else {
            message(context, PlayerMessageKeys.PLAYER_PLAYBACK_STATE_UNCHANGED)
        }
    }

    private suspend fun resume(context: CommandContext, actor: PlayerActor): String {
        val snapshot = actions.snapshot(actor)?.snapshot
        if (snapshot == null) return message(context, PlayerMessageKeys.ERROR_NO_GUILD)
        if (!snapshot.hasActiveTrack) return message(context, PlayerMessageKeys.ERROR_NO_TRACK_PLAYING)
        return if (actions.resume(actor)) {
            message(context, PlayerMessageKeys.PLAYER_RESUMED)
        } else {
            message(context, PlayerMessageKeys.PLAYER_PLAYBACK_STATE_UNCHANGED)
        }
    }

    private suspend fun skip(context: CommandContext, actor: PlayerActor): String =
        if (actions.skip(actor)) {
            message(context, PlayerMessageKeys.SKIP_COMMAND_RESPONSE)
        } else {
            message(context, PlayerMessageKeys.ERROR_NO_TRACK_PLAYING)
        }

    private suspend fun stop(context: CommandContext, actor: PlayerActor): String {
        actions.stop(actor)
        return message(context, PlayerMessageKeys.PLAYER_STOPPED)
    }

    /** Queue details are rendered only here; provider failure text is never echoed. */
    private suspend fun describeQueue(context: CommandContext, queueSnapshot: PlayerQueueSnapshot?): String {
        val snapshot: PlayerSnapshot = queueSnapshot?.snapshot
            ?: return message(context, PlayerMessageKeys.ERROR_NO_GUILD)
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

    private fun actor(context: CommandContext): PlayerActor? = context.guildId?.let { guildId ->
        PlayerActor(
            guildId = guildId,
            userId = context.userId,
            channelId = context.channelId,
            guildLocale = context.guildLocale,
        )
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

    private companion object {
        private val LOG = LoggerFactory.getLogger(PlayerCommandController::class.java)

        private const val MAX_LISTED_TRACKS = 10
    }
}
