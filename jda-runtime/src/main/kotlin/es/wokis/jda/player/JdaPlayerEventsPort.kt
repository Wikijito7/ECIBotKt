package es.wokis.jda.player

import es.wokis.commands.player.PlayerEventsPort
import es.wokis.commands.player.PlayerPlaybackState
import es.wokis.commands.player.PlayerVoiceTarget
import es.wokis.player.engine.event.PlayerEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.StateFlow
import net.dv8tion.jda.api.entities.Guild

/**
 * [PlayerEventsPort] over one guild's voice adapter.
 *
 * The adapter keeps the buffered fan-out and the presentation revision; this port only maps them to
 * neutral values and resolves the voice channel of a connect request by id.
 */
internal class JdaPlayerEventsPort(
    private val guild: Guild,
    private val adapter: JdaGuildPlayerVoiceAdapter,
) : PlayerEventsPort {

    override val events: Flow<PlayerEvent> = adapter.events

    override val playbackState: StateFlow<PlayerPlaybackState> = MappedPlaybackState(adapter.presentation)

    /** @throws IllegalStateException when the target channel is no longer available, like a failed connect. */
    override suspend fun connect(target: PlayerVoiceTarget): Boolean {
        val channel = target.channelId.value
            .takeIf { it <= Long.MAX_VALUE.toULong() }
            ?.let { guild.getVoiceChannelById(it.toLong()) }
            ?: error("The player voice channel is not available to the bot")
        return adapter.connect(channel)
    }
}

/** Read-through view of the adapter presentation, so `.value` never lags behind the adapter. */
private class MappedPlaybackState(
    private val source: StateFlow<JdaPlayerPresentation>,
) : StateFlow<PlayerPlaybackState> {

    override val value: PlayerPlaybackState
        get() = source.value.toPlaybackState()

    override val replayCache: List<PlayerPlaybackState>
        get() = listOf(value)

    override suspend fun collect(collector: FlowCollector<PlayerPlaybackState>): Nothing =
        source.collect(FlowCollector { collector.emit(it.toPlaybackState()) })
}

private fun JdaPlayerPresentation.toPlaybackState(): PlayerPlaybackState = PlayerPlaybackState(
    revision = revision,
    activeTrack = activeTrack,
    loadFailed = loadFailure != null,
)
