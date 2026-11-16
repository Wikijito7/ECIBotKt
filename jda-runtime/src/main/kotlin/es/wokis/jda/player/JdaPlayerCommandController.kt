package es.wokis.jda.player

import es.wokis.commands.neutral.CommandContext
import es.wokis.domain.model.UserId
import es.wokis.jda.command.toJdaId
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
 * Orchestrates `/jda-play` and `/jda-player`. Load happens before connect, so voice is joined only
 * once the adapter reports an active track.
 */
internal class JdaPlayerCommandController(
    private val jda: JDA,
    private val runtime: JdaPlayerRuntime,
) {

    suspend fun play(context: CommandContext, rawInput: String): String {
        val input = rawInput.trim()
        if (input.isEmpty()) return EMPTY_INPUT_MESSAGE
        return when (val target = resolveTarget(context)) {
            is JdaPlayerVoiceTarget.Unavailable -> target.message
            is JdaPlayerVoiceTarget.Ready -> startPlayback(target, input)
        }
    }

    suspend fun control(context: CommandContext, rawAction: String): String {
        val action = JdaPlayerAction.parse(rawAction) ?: return unknownActionMessage()
        val guild = context.guildId?.let { guildId -> jda.getGuildById(guildId.toJdaId()) }
            ?: return GUILD_ONLY_MESSAGE
        val engine = runtime.engineFor(guild)
        return when (action) {
            JdaPlayerAction.QUEUE -> describeQueue(engine.snapshot())
            JdaPlayerAction.PAUSE -> pause(engine)
            JdaPlayerAction.RESUME -> resume(engine)
            JdaPlayerAction.SKIP -> skip(engine)
            JdaPlayerAction.STOP -> stop(engine)
            JdaPlayerAction.DISCONNECT -> disconnect(guild, engine)
            JdaPlayerAction.RECONNECT -> reconnect(context)
        }
    }

    private suspend fun startPlayback(target: JdaPlayerVoiceTarget.Ready, input: String): String {
        val engine = runtime.engineFor(target.guild)
        val adapter = runtime.voiceAdapterFor(target.guild)
        val baseRevision = adapter.presentation.value.revision

        engine.loadAndPlay(input)

        return when (val start = awaitPlaybackStart(adapter, baseRevision)) {
            JdaPlayerPlaybackStart.TimedOut -> TIMEOUT_MESSAGE
            is JdaPlayerPlaybackStart.Failed -> start.reason
            JdaPlayerPlaybackStart.Started -> connectMessage(target) { CONNECTED_MESSAGE }
        }
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
        target: JdaPlayerVoiceTarget.Ready,
        onConnected: () -> String,
    ): String = try {
        if (runtime.connect(target.guild, target.channel)) onConnected() else SESSION_REFUSED_MESSAGE
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Exception) {
        LOG.warn("The player voice connection failed ({})", error.javaClass.simpleName)
        CONNECTION_FAILED_MESSAGE
    }

    private suspend fun disconnect(guild: Guild, engine: PlayerEngine): String {
        engine.stop()
        runtime.disconnect(guild)
        return DISCONNECTED_MESSAGE
    }

    private suspend fun reconnect(context: CommandContext): String =
        when (val target = resolveTarget(context)) {
            is JdaPlayerVoiceTarget.Unavailable -> target.message
            is JdaPlayerVoiceTarget.Ready -> connectMessage(target) {
                val snapshot = runtime.engineFor(target.guild).snapshot()
                if (snapshot.hasActiveTrack) RECONNECTED_PLAYING_MESSAGE else RECONNECTED_IDLE_MESSAGE
            }
        }

    private suspend fun pause(engine: PlayerEngine): String {
        if (!engine.snapshot().hasActiveTrack) return NO_ACTIVE_PLAYBACK_MESSAGE
        return if (engine.pause()) PAUSED_MESSAGE else PLAYBACK_STATE_UNCHANGED_MESSAGE
    }

    private suspend fun resume(engine: PlayerEngine): String {
        if (!engine.snapshot().hasActiveTrack) return NO_ACTIVE_PLAYBACK_MESSAGE
        return if (engine.resume()) RESUMED_MESSAGE else PLAYBACK_STATE_UNCHANGED_MESSAGE
    }

    private suspend fun skip(engine: PlayerEngine): String =
        if (engine.skip()) SKIPPED_MESSAGE else NOTHING_PLAYING_MESSAGE

    private suspend fun stop(engine: PlayerEngine): String {
        engine.stop()
        return STOPPED_MESSAGE
    }

    /** Queue details are rendered only here; provider failure text is never echoed. */
    private fun describeQueue(snapshot: PlayerSnapshot): String {
        val current = snapshot.current?.displayName ?: "Nothing is playing."
        val state = if (snapshot.paused) "paused" else "playing"
        val upcoming = snapshot.queue.take(MAX_LISTED_TRACKS).mapIndexed { index, track ->
            "${index + 1}. ${track.displayName}"
        }
        return buildString {
            append("Current (").append(state).append("): ").append(current)
            append(" | queued: ").append(snapshot.queue.size)
            if (upcoming.isNotEmpty()) {
                append('\n').append(upcoming.joinToString(separator = "\n"))
            }
            if (snapshot.queue.size > upcoming.size) {
                append('\n').append("…and ").append(snapshot.queue.size - upcoming.size).append(" more")
            }
        }
    }

    private fun unknownActionMessage(): String =
        "Unknown action. Use one of: ${JdaPlayerAction.names.joinToString(", ")}."

    private fun resolveTarget(context: CommandContext): JdaPlayerVoiceTarget {
        val guild = context.guildId?.let { guildId -> jda.getGuildById(guildId.toJdaId()) }
            ?: return JdaPlayerVoiceTarget.Unavailable(GUILD_ONLY_MESSAGE)
        val channel = guild.getMemberById(context.userId.toJdaId())?.voiceState?.channel
        return if (channel is VoiceChannel) {
            JdaPlayerVoiceTarget.Ready(guild, channel)
        } else {
            JdaPlayerVoiceTarget.Unavailable(NOT_IN_VOICE_MESSAGE)
        }
    }

    private companion object {
        private val LOG = LoggerFactory.getLogger(JdaPlayerCommandController::class.java)

        private const val LOAD_START_TIMEOUT_MILLIS = 30_000L
        private const val MAX_LISTED_TRACKS = 10

        private const val EMPTY_INPUT_MESSAGE = "Provide a track name, URL or playlist for the player."
        private const val GUILD_ONLY_MESSAGE =
            "Run this in a guild, from the voice channel the bot should join."
        private const val NOT_IN_VOICE_MESSAGE =
            "Join a regular voice channel first; stage channels are not supported by the player."
        private const val TIMEOUT_MESSAGE =
            "The load did not produce an active track in time, so the bot did not connect."
        private const val CONNECTED_MESSAGE = "Loaded and playing; the bot joined your voice channel."
        private const val RECONNECTED_PLAYING_MESSAGE =
            "Reconnected to your voice channel; the current track and queue were preserved."
        private const val RECONNECTED_IDLE_MESSAGE =
            "Reconnected to your voice channel; nothing is playing right now."
        private const val SESSION_REFUSED_MESSAGE =
            "Playback started, but the voice session is owned by another voice path. " +
                "Disconnect it and use reconnect."
        private const val CONNECTION_FAILED_MESSAGE =
            "The voice connection could not be opened; check the bot's voice permissions."
        private const val DISCONNECTED_MESSAGE = "Queue cleared and voice disconnected."
        private const val PAUSED_MESSAGE = "Playback paused."
        private const val RESUMED_MESSAGE = "Playback resumed."
        private const val PLAYBACK_STATE_UNCHANGED_MESSAGE =
            "Playback state did not change; start a track first or check the current state."
        private const val NO_ACTIVE_PLAYBACK_MESSAGE =
            "Nothing is playing right now, so there is no playback to pause or resume."
        private const val SKIPPED_MESSAGE = "Skipped to the next track."
        private const val NOTHING_PLAYING_MESSAGE = "Nothing is playing right now."
        private const val STOPPED_MESSAGE = "Queue cleared and playback stopped."
    }
}

private fun UserId.toJdaId(): Long {
    require(value <= Long.MAX_VALUE.toULong()) {
        "User id does not fit in JDA's signed 64-bit id range"
    }
    return value.toLong()
}
