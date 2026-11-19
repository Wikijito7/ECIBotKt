package es.wokis.commands.player

import es.wokis.domain.model.ChannelId
import es.wokis.domain.model.GuildId
import es.wokis.localization.GuildLocalizationService
import es.wokis.message.EmbedView
import es.wokis.message.MessageView
import es.wokis.player.PlayerMessageId
import es.wokis.player.PlayerMessagePort
import es.wokis.player.engine.PlayerEngine
import es.wokis.player.engine.event.PlayerEvent
import es.wokis.player.engine.model.PlayerSnapshot
import es.wokis.player.message.PlayerMessageViewFactory
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.TestScope

/**
 * Fixture for [PlayerMessageCoordinator] without any gateway type.
 *
 * The coordinator only sees strict MockK seams plus a fake [PlayerEventsPort]; the recordings here
 * are the neutral equivalent of the old channel spies, so every assertion still proves the exact
 * channel, message id and payload the coordinator produced.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class PlayerMessageCoordinatorFixture(
    scope: TestScope,
    localization: GuildLocalizationService,
) : AutoCloseable {

    val eventFlow = MutableSharedFlow<PlayerEvent>(extraBufferCapacity = 64)
    val playback = MutableStateFlow(PlayerPlaybackState())
    val eventsPort = FakePlayerEventsPort(eventFlow, playback)
    val channelPort: PlayerChannelPort = mockk()
    val messagePort: PlayerMessagePort = mockk()
    val voiceTargets: PlayerVoiceTargetPort = mockk()
    val viewFactory: PlayerMessageViewFactory = mockk()
    val engine: PlayerEngine = mockk()

    val messageId = PlayerMessageId(MESSAGE_ID.toULong())
    val target = PlayerVoiceTarget(channelId = ChannelId(VOICE_CHANNEL_ID.toULong()), channelName = "General")

    val commandChannelId = ChannelId(COMMAND_CHANNEL_ID.toULong())
    val playerChannelId = ChannelId(PLAYER_CHANNEL_ID.toULong())

    val sentTexts = mutableListOf<String>()
    val sentViews = mutableListOf<MessageView>()
    val sentChannelIds = mutableListOf<ChannelId>()
    val editedTexts = mutableListOf<String>()
    val editedViews = mutableListOf<MessageView>()
    val editedChannels = mutableListOf<ChannelId>()
    val editedMessageIds = mutableListOf<PlayerMessageId>()
    val viewSnapshots = mutableListOf<PlayerSnapshot>()

    var snapshotValue = PlayerSnapshot()
    var channelSetup: PlayerChannelSetup =
        PlayerChannelSetup.Created(channelId = playerChannelId, messageId = messageId)
    var viewCreationFailure: Throwable? = null
    var leaveCalls = 0
        private set

    val view = MessageView(embed = EmbedView(title = "player-view"))

    val coordinator = PlayerMessageCoordinator(
        initialGuildId = GuildId(GUILD_ID.toULong()),
        guildName = { "Jungle" },
        engine = engine,
        eventsPort = eventsPort,
        localizationService = localization,
        channelPort = channelPort,
        messagePort = messagePort,
        voiceTargets = voiceTargets,
        viewFactory = viewFactory,
        scope = scope,
        onLeave = { leaveCalls++ },
    )

    init {
        every { engine.snapshot() } answers { snapshotValue }
        coEvery { engine.loadAndPlay(any()) } returns Unit
        coEvery { channelPort.setUp(any()) } answers { channelSetup }
        coEvery { voiceTargets.channelName(any()) } returns "General"
        coEvery { viewFactory.createPlayerView(any(), any(), any(), any()) } coAnswers {
            viewCreationFailure?.let { throw it }
            viewSnapshots += secondArg<PlayerSnapshot>()
            view
        }
        // The neutral ids are value classes, so their JVM argument arrives unboxed as a Long.
        coEvery { messagePort.send(any(), any<String>()) } coAnswers {
            sentChannelIds += ChannelId(firstArg<Long>().toULong())
            sentTexts += secondArg<String>()
            messageId
        }
        coEvery { messagePort.send(any(), any<MessageView>()) } coAnswers {
            sentChannelIds += ChannelId(firstArg<Long>().toULong())
            sentViews += secondArg<MessageView>()
            messageId
        }
        coEvery { messagePort.edit(any(), any(), any<String>()) } coAnswers {
            editedChannels += ChannelId(firstArg<Long>().toULong())
            editedMessageIds += PlayerMessageId(secondArg<Long>().toULong())
            editedTexts += thirdArg<String>()
        }
        coEvery { messagePort.edit(any(), any(), any<MessageView>()) } coAnswers {
            editedChannels += ChannelId(firstArg<Long>().toULong())
            editedMessageIds += PlayerMessageId(secondArg<Long>().toULong())
            editedViews += thirdArg<MessageView>()
        }
    }

    fun recordCommand(channelName: String? = "General") {
        coordinator.recordCommand(
            channelId = commandChannelId,
            guildId = GuildId(GUILD_ID.toULong()),
            guildLocale = null,
            channelName = channelName,
        )
    }

    fun emit(event: PlayerEvent) {
        eventFlow.tryEmit(event)
    }

    fun startPlayback(input: String) {
        coordinator.startPlayback(target, input)
    }

    suspend fun ensurePlayerView(): String = coordinator.ensurePlayerView()

    fun currentPlayerChannelId(): ChannelId? = privateField("playerChannelId") as? ChannelId

    fun currentPlayerMessageId(): PlayerMessageId? = privateField("playerMessageId") as? PlayerMessageId

    override fun close() {
        coordinator.close()
    }

    private fun privateField(name: String): Any? = PlayerMessageCoordinator::class.java
        .getDeclaredField(name)
        .apply { isAccessible = true }
        .get(coordinator)

    private companion object {
        const val GUILD_ID = 42L
        const val COMMAND_CHANNEL_ID = 43L
        const val PLAYER_CHANNEL_ID = 77L
        const val VOICE_CHANNEL_ID = 99L
        const val MESSAGE_ID = 900L
    }
}

/** Recording fake so the coordinator can wait on revisions and observe a refused or failed connect. */
internal class FakePlayerEventsPort(
    eventSource: MutableSharedFlow<PlayerEvent>,
    playbackSource: MutableStateFlow<PlayerPlaybackState>,
) : PlayerEventsPort {

    override val events: Flow<PlayerEvent> = eventSource

    override val playbackState: StateFlow<PlayerPlaybackState> = playbackSource

    val connected = mutableListOf<PlayerVoiceTarget>()

    var connectResult = true
    var connectFailure: Throwable? = null

    override suspend fun connect(target: PlayerVoiceTarget): Boolean {
        connected += target
        connectFailure?.let { throw it }
        return connectResult
    }
}
