package es.wokis.jda.player

import es.wokis.commands.player.NextTrackResult
import es.wokis.commands.player.PlayerActionsPort
import es.wokis.commands.player.PlayerActor
import es.wokis.commands.player.PlayerQueueSnapshot
import es.wokis.commands.player.PlayerReconnectResult
import es.wokis.jda.command.toJdaId
import es.wokis.player.engine.PlayerEngine
import kotlinx.coroutines.CancellationException
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel
import org.slf4j.LoggerFactory

/**
 * JDA implementation of the neutral player action seam.
 *
 * It acts through the same runtime seams the `/play` and `/player` controllers use, so every
 * surface shares one engine, one voice adapter and one `#player` message per guild. No gateway type
 * crosses back: unusable guilds and failed attempts are reported through the neutral results.
 */
internal class JdaPlayerActionsPort(
    private val jda: JDA,
    private val runtime: JdaPlayerRuntime,
) : PlayerActionsPort {

    override suspend fun isVoiceAvailable(actor: PlayerActor): Boolean =
        guildFor(actor)?.let { guild -> callerVoiceChannel(guild, actor) != null } == true

    override suspend fun pause(actor: PlayerActor): Boolean = withEngine(actor) { engine ->
        engine.snapshot().hasActiveTrack && engine.pause()
    } ?: false

    override suspend fun resume(actor: PlayerActor): Boolean = withEngine(actor) { engine ->
        engine.snapshot().hasActiveTrack && engine.resume()
    } ?: false

    override suspend fun skip(actor: PlayerActor): Boolean = withEngine(actor) { it.skip() } ?: false

    override suspend fun shuffle(actor: PlayerActor): Boolean = withEngine(actor) { it.shuffle() } ?: false

    override suspend fun stop(actor: PlayerActor): Boolean = withEngine(actor) { it.stop() } ?: false

    override suspend fun moveNext(actor: PlayerActor, input: String): NextTrackResult {
        // An unreachable guild cannot match a queued track, so it reports the search fallback.
        val engine = guildFor(actor)?.let(runtime::engineFor) ?: return NextTrackResult.NotInQueue
        return if (engine.isQueueEmpty()) {
            NextTrackResult.QueueEmpty
        } else {
            engine.moveTrackToNext(input)?.let { NextTrackResult.Moved(it.displayName) }
                ?: NextTrackResult.NotInQueue
        }
    }

    override suspend fun loadNext(actor: PlayerActor, input: String) {
        val guild = guildFor(actor) ?: return
        // Kord parity: the load announces into the channel the command was used in.
        runtime.messagesFor(guild).recordCommand(
            channelId = actor.channelId,
            guildId = actor.guildId,
            guildLocale = actor.guildLocale,
            channelName = callerVoiceChannel(guild, actor)?.name,
        )
        runtime.engineFor(guild).loadAndPlay(url = input, addToFront = true)
    }

    override suspend fun disconnect(actor: PlayerActor) {
        guildFor(actor)?.let { guild -> runtime.disconnect(guild) }
    }

    override suspend fun reconnect(actor: PlayerActor): PlayerReconnectResult {
        val guild = guildFor(actor) ?: return PlayerReconnectResult.NOT_CONNECTED
        val channel = callerVoiceChannel(guild, actor) ?: return PlayerReconnectResult.NOT_CONNECTED
        return try {
            when (runtime.reconnect(guild, channel)) {
                JdaPlayerReconnectResult.Reconnected -> PlayerReconnectResult.RECONNECTED
                JdaPlayerReconnectResult.NotConnected -> PlayerReconnectResult.NOT_CONNECTED
                JdaPlayerReconnectResult.ForeignSession,
                JdaPlayerReconnectResult.InProgress,
                JdaPlayerReconnectResult.TimedOut,
                -> PlayerReconnectResult.FAILED
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            LOG.warn("The player reconnect failed ({})", error.javaClass.simpleName)
            PlayerReconnectResult.FAILED
        }
    }

    override suspend fun snapshot(actor: PlayerActor): PlayerQueueSnapshot? =
        guildFor(actor)?.let { guild -> PlayerQueueSnapshot(guild.name, runtime.engineFor(guild).snapshot()) }

    private fun guildFor(actor: PlayerActor): Guild? = jda.getGuildById(actor.guildId.toJdaId())

    private suspend fun <T> withEngine(actor: PlayerActor, action: suspend (PlayerEngine) -> T): T? =
        guildFor(actor)?.let { guild -> action(runtime.engineFor(guild)) }

    private fun callerVoiceChannel(guild: Guild, actor: PlayerActor): VoiceChannel? =
        guild.getMemberById(actor.userId.toJdaId())?.voiceState?.channel as? VoiceChannel

    private companion object {
        private val LOG = LoggerFactory.getLogger(JdaPlayerActionsPort::class.java)
    }
}
