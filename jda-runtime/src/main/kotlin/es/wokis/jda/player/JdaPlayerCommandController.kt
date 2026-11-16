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
 * Adapter-local orchestration for the minimal JDA player command surface.
 *
 * It resolves the invoking member's voice channel, drives the shared [PlayerEngine] and the
 * per-guild [JdaGuildPlayerVoiceAdapter], and returns plain text so the command handlers stay thin
 * and gateway-free. All waits are coroutine suspensions on the command/router scope: no thread is
 * blocked, and the interaction is deferred by the handler before the long provider load starts.
 *
 * **Load-then-connect.** `/jda-play` loads first and connects only after the adapter reports an
 * active track. Connecting earlier would build the audio stream against a player that is not
 * playing yet and, in the worst case, leave the send handler permanently exhausted.
 *
 * **Deliberate semantics.** `disconnect` clears the queue (owner decision) and closes the voice
 * connection; `reconnect` rejoins the caller's channel without touching the current track or the
 * pending queue; `stop` clears the queue and stops playback, after which the adapter closes the
 * voice connection on the engine's `Stopped` event. Commands never collect the engine event stream:
 * the adapter is its only subscriber, and this class only reads the adapter's derived presentation.
 *
 * `pause` and `resume` are additionally guarded by a controller-level snapshot: an idle player has
 * no owning track, so the reply is a fixed no-active-playback message and the engine is never asked
 * to transition. A rejected transition on an active track still reports the unchanged state.
 *
 * There is no hot reload, configuration mutation or owner/admin config command here: configuration
 * is read once at startup, and changing it requires a restart.
 */
internal class JdaPlayerCommandController(
    private val jda: JDA,
    private val runtime: JdaPlayerRuntime,
) {

    /** Loads [rawInput] and joins the caller's voice channel once playback is active. */
    suspend fun play(context: CommandContext, rawInput: String): String {
        val input = rawInput.trim()
        if (input.isEmpty()) return EMPTY_INPUT_MESSAGE
        return when (val target = resolveTarget(context)) {
            is JdaPlayerVoiceTarget.Unavailable -> target.message
            is JdaPlayerVoiceTarget.Ready -> startPlayback(target, input)
        }
    }

    /** Applies one control [rawAction], returning a user-facing result message. */
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

        // Non-blocking: the engine queues the load on its actor and returns once it was accepted.
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

    /**
     * Connects and renders the outcome.
     *
     * A refused connection (another voice path owns the session) and a failed one (permissions,
     * closed runtime) are reported separately so a staging run can tell them apart. Cancellation is
     * rethrown; no gateway exception message is echoed.
     */
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
        // Owner decision: disconnecting also clears the queue instead of resuming it later.
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

    /**
     * Guards a transport transition behind the presence of an owning track.
     *
     * An idle player has nothing to pause or resume, so the engine is not asked to transition and
     * the fixed no-active-playback reply is returned instead of a success acknowledgement. The
     * snapshot read is cheap and synchronous, and the engine remains the single owner of the
     * transition: a rejected transition on an active track still reports the unchanged state.
     */
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

    /**
     * Renders queue diagnostics for the administrator-only, ephemeral staging command.
     *
     * Track display names are shown here and only here: the caller is the staging administrator
     * diagnosing this guild's queue. Raw provider failure diagnostics stay suppressed at the
     * presentation boundary, so no exception reason is ever echoed.
     */
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
            "Run this in the development guild, from the voice channel the bot should join."
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

/**
 * Converts the neutral unsigned user id into JDA's signed 64-bit id.
 *
 * Discord snowflakes fit in the signed range in practice; a larger value is rejected instead of
 * wrapping around and addressing a different member. The failure message is deliberately
 * value-free, like every other JDA snowflake range check, so the rejected id is never echoed.
 */
private fun UserId.toJdaId(): Long {
    require(value <= Long.MAX_VALUE.toULong()) {
        "User id does not fit in JDA's signed 64-bit id range"
    }
    return value.toLong()
}
