package es.wokis.commands.player

import es.wokis.domain.locale.LocaleCode
import es.wokis.domain.model.ChannelId
import es.wokis.domain.model.GuildId
import es.wokis.domain.model.UserId
import es.wokis.player.engine.model.PlayerSnapshot

/**
 * Guild, caller and invoking channel one player action acts for.
 *
 * [guildLocale] lets the adapter address the invoking guild's default locale when it publishes
 * event-driven messages that are not tied to an interaction response.
 */
data class PlayerActor(
    val guildId: GuildId,
    val userId: UserId,
    val channelId: ChannelId,
    val guildLocale: LocaleCode? = null,
)

/**
 * Outcome of moving a queued track to the next position; mirrors Kord's `/next` branches.
 */
sealed interface NextTrackResult {

    /** The queue is empty, so no track could be matched. */
    data object QueueEmpty : NextTrackResult

    /** The input matched a queued track and it now plays next. */
    data class Moved(val trackName: String) : NextTrackResult

    /** No queued track matched, so the input must be handed to the provider as a next-position load. */
    data object NotInQueue : NextTrackResult
}

/** Outcome of a reconnect request; Kord only distinguishes "reconnected" from "not connected". */
enum class PlayerReconnectResult {
    RECONNECTED,
    NOT_CONNECTED,

    /** The gateway reported a transient or foreign session, or the attempt failed. */
    FAILED,
}

/** Guild name plus the player state the `/queue` embed renders. */
data class PlayerQueueSnapshot(
    val guildName: String,
    val snapshot: PlayerSnapshot,
)

/**
 * Gateway-neutral seam over one guild's player transport, implemented by each gateway adapter.
 *
 * Every action requires the caller to be in a voice channel, which is Kord's
 * `getOrCreateLavaPlayerService` precondition; [PlayerVoiceGate.isVoiceAvailable] lets handlers reply
 * the matching localized error before acting. Implementations must not throw for an unusable guild:
 * they report it through the return value instead.
 */
interface PlayerActionsPort : PlayerPlaybackToggles, PlayerVoiceGate {

    /** Skips the current track; false when nothing plays. */
    suspend fun skip(actor: PlayerActor): Boolean

    /** Reorders the pending queue randomly; false when the queue did not change. */
    suspend fun shuffle(actor: PlayerActor): Boolean

    /** Stops playback and clears the queue without closing the voice connection. */
    suspend fun stop(actor: PlayerActor): Boolean

    /** Moves a queued track matching [input] to the next position. */
    suspend fun moveNext(actor: PlayerActor, input: String): NextTrackResult

    /** Loads [input] as the next track, keeping the current one playing. */
    suspend fun loadNext(actor: PlayerActor, input: String)

    /** Closes the voice connection; callers stop playback first with [stop]. */
    suspend fun disconnect(actor: PlayerActor)

    /** Reconnects to the actor's voice channel, preserving the current track and queue. */
    suspend fun reconnect(actor: PlayerActor): PlayerReconnectResult

    /** Current state of the actor's guild, or null when the guild is unreachable. */
    suspend fun snapshot(actor: PlayerActor): PlayerQueueSnapshot?
}

/** Voice precondition shared by every audio command seam. */
interface PlayerVoiceGate {

    /** True when the actor's guild is reachable and the actor is in a voice channel of that guild. */
    suspend fun isVoiceAvailable(actor: PlayerActor): Boolean
}

/** Gateway-neutral seam for non-search audio: local sounds and synthesized speech. */
interface AudioContentActionsPort : PlayerVoiceGate {

    /** Plays the local sound named [soundName]; the adapter owns the folder and file format. */
    suspend fun playSound(actor: PlayerActor, soundName: String)

    /** Queues [text] as synthesized speech; the adapter owns the provider URI shape. */
    suspend fun loadTts(actor: PlayerActor, text: String)
}

/** Transport state toggles of the player seam. */
interface PlayerPlaybackToggles {

    /** Pauses the current track; false when nothing plays or the state did not change. */
    suspend fun pause(actor: PlayerActor): Boolean

    /** Resumes the paused track; false when nothing plays or the state did not change. */
    suspend fun resume(actor: PlayerActor): Boolean
}
