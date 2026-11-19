package es.wokis.jda.player

import es.wokis.domain.locale.GetGuildLocaleUseCase
import es.wokis.domain.locale.LocaleCode
import es.wokis.domain.locale.LocaleRepository
import es.wokis.domain.model.ChannelId
import es.wokis.domain.model.GuildId
import es.wokis.localization.GuildLocalizationService
import es.wokis.localization.LocalizationCatalog
import es.wokis.localization.PlayerMessageKeys
import es.wokis.message.EmbedView
import es.wokis.message.MessageView
import es.wokis.player.PlayerMessageId
import es.wokis.player.engine.PlayerEngine
import es.wokis.player.engine.event.PlayerEvent
import es.wokis.player.engine.model.PlayerSnapshot
import es.wokis.player.engine.model.PlayerTrack
import es.wokis.player.message.PlayerMessageViewFactory
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction
import net.dv8tion.jda.api.requests.restaction.MessageEditAction
import net.dv8tion.jda.api.utils.messages.MessageCreateData
import net.dv8tion.jda.api.utils.messages.MessageEditData
import org.junit.jupiter.api.Test
import java.util.function.Consumer
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class JdaPlayerMessageCoordinatorTest {

    @Test
    fun `Given every track queued variant When handled Then emit the matching queue key`() = coordinatorTest { fixture ->
        val track = track()
        val url = "https://music.example/audio"
        fixture.recordCommand()

        fixture.emit(PlayerEvent.TrackQueued(track, addToFront = false, uri = null))
        fixture.emit(PlayerEvent.TrackQueued(track, addToFront = true, uri = null))
        fixture.emit(PlayerEvent.TrackQueued(track, addToFront = false, uri = url))
        fixture.emit(PlayerEvent.TrackQueued(track, addToFront = true, uri = url))
        advanceUntilIdle()

        assertEquals(
            listOf(
                "Added Artist - Song to the queue",
                "Added Artist - Song to the next position in queue",
                "Added [Artist - Song](https://music.example/audio) to the queue",
                "Added [Artist - Song](https://music.example/audio) to the next position in queue",
            ),
            fixture.commandChannel.sentTexts,
        )
    }

    @Test
    fun `Given a playlist When queued Then send the found list and edit it into the added songs text`() =
        coordinatorTest { fixture ->
            fixture.recordCommand()

            fixture.emit(PlayerEvent.PlaylistQueued("My Mix", trackCount = 5, playlistUrl = null, addToFront = false))
            fixture.emit(
                PlayerEvent.PlaylistQueued(
                    "Next Mix",
                    trackCount = 2,
                    playlistUrl = "https://music.example/list",
                    addToFront = true,
                ),
            )
            advanceUntilIdle()

            assertEquals(
                listOf(
                    "Found track list My Mix with 5 tracks",
                    "Found track list Next Mix with 2 tracks",
                ),
                fixture.commandChannel.sentTexts,
            )
            assertEquals(
                listOf(
                    "Added My Mix (5 tracks) to the queue",
                    "Added [Next Mix](https://music.example/list) (2 tracks) to the next position in queue",
                ),
                fixture.commandChannel.editedTexts,
            )
            fixture.commandChannel.channel.let { channel ->
                verify(exactly = 2) { channel.editMessageById(MESSAGE_ID, any<String>()) }
            }
        }

    @Test
    fun `Given no matches When handled Then echo only the event input`() = coordinatorTest { fixture ->
        fixture.recordCommand()

        fixture.emit(PlayerEvent.NoMatches("unknown song"))
        advanceUntilIdle()

        assertEquals(listOf("No matches found for unknown song"), fixture.commandChannel.sentTexts)
    }

    @Test
    fun `Given a load failure carrying a synthetic secret When handled Then never echo provider text`() =
        coordinatorTest { fixture ->
            val secret = "provider-secret-sentinel"
            val credentialUrl = "https://user:$secret@stream.example.invalid/audio?token=$secret"
            fixture.recordCommand()

            fixture.emit(PlayerEvent.LoadFailed(credentialUrl))
            advanceUntilIdle()

            val message = fixture.commandChannel.sentTexts.single()
            assertEquals(
                english(PlayerMessageKeys.LOAD_FAILED, english(PlayerMessageKeys.PLAYER_LOAD_REJECTED)),
                message,
            )
            assertFalse(message.contains(secret), "the reply leaked the provider secret: $message")
            assertFalse(message.contains("stream.example.invalid"), "the reply leaked the provider endpoint")
            assertFalse(message.contains("https://"), "the reply leaked the provider URL")
        }

    @Test
    fun `Given a playback failure When handled Then report the playback category only`() = coordinatorTest { fixture ->
        fixture.recordCommand()

        fixture.emit(PlayerEvent.PlaybackFailed("stream decoder exploded"))
        advanceUntilIdle()

        val message = fixture.commandChannel.sentTexts.single()
        assertEquals(
            english(PlayerMessageKeys.LOAD_FAILED, english(PlayerMessageKeys.PLAYER_PLAYBACK_FAILED)),
            message,
        )
        assertFalse(message.contains("stream decoder exploded"))
    }

    @Test
    fun `Given a fresh guild When the first track starts Then announce now playing and never touch the player channel`() =
        coordinatorTest { fixture ->
            fixture.snapshot = PlayerSnapshot(current = track(), hasActiveTrack = true)
            fixture.recordCommand(channelName = "General")

            fixture.emit(PlayerEvent.TrackStarted(track()))
            advanceUntilIdle()

            assertEquals(
                listOf("🎶 Playing Artist - Song in General"),
                fixture.commandChannel.sentTexts,
            )
            coVerify(exactly = 0) { fixture.channelService.setUp(any(), any()) }
            coVerify(exactly = 0) { fixture.viewFactory.createPlayerView(any(), any(), any(), any()) }
            assertTrue(
                fixture.playerChannel.sentData.isEmpty(),
                "only /player may create the embed; a track start must not touch #player",
            )
        }

    @Test
    fun `Given an existing player embed When another track starts Then edit it without announcing again`() =
        coordinatorTest { fixture ->
            fixture.snapshot = PlayerSnapshot(current = track(), hasActiveTrack = true)
            fixture.recordCommand()
            fixture.ensurePlayerView()
            advanceUntilIdle()
            val announcements = fixture.commandChannel.sentTexts.toList()

            fixture.snapshot = PlayerSnapshot(current = track(title = "Next"), hasActiveTrack = true)
            fixture.emit(PlayerEvent.TrackStarted(track(title = "Next")))
            advanceUntilIdle()

            assertEquals(announcements, fixture.commandChannel.sentTexts, "now playing is announced only once")
            assertEquals(1, fixture.playerChannel.editedData.size, "the second start must edit the existing embed")
            assertFalse(
                fixture.commandChannel.sentTexts.any { it.contains("Playing Artist - Next") },
                "an existing embed must not re-announce now playing",
            )
        }

    @Test
    fun `Given a renamed target channel When the track starts Then announce the name resolved at track start`() =
        coordinatorTest { fixture ->
            fixture.presentation.value = JdaPlayerPresentation(revision = 1, activeTrack = true)
            fixture.snapshot = PlayerSnapshot(current = track(), hasActiveTrack = true)
            fixture.recordCommand(channelName = "Stale name")

            fixture.startPlayback("track")
            fixture.emit(PlayerEvent.TrackStarted(track()))
            advanceUntilIdle()

            assertEquals(
                "🎶 Playing Artist - Song in General",
                fixture.commandChannel.sentTexts.first(),
            )
            verify(exactly = 1) { fixture.jda.getVoiceChannelById(VOICE_CHANNEL_ID) }
        }

    @Test
    fun `Given an existing player message When later events arrive Then edit it instead of creating another`() =
        coordinatorTest { fixture ->
            fixture.snapshot = PlayerSnapshot(current = track(), hasActiveTrack = true)
            fixture.recordCommand()
            fixture.ensurePlayerView()
            advanceUntilIdle()

            fixture.snapshot = PlayerSnapshot(current = track(title = "Next"), hasActiveTrack = true)
            fixture.emit(PlayerEvent.QueueChanged(fixture.snapshot))
            fixture.emit(PlayerEvent.TrackEnded(queueEmpty = false))
            advanceUntilIdle()

            coVerify(exactly = 1) { fixture.channelService.setUp(fixture.guild, fixture.view) }
            assertEquals(2, fixture.playerChannel.editedData.size, "later events must edit the same embed")
            fixture.playerChannel.channel.let { channel ->
                verify(exactly = 2) { channel.editMessageById(MESSAGE_ID, any<MessageEditData>()) }
            }
        }

    @Test
    fun `Given a stopped player When handled Then edit the embed with the empty state`() = coordinatorTest { fixture ->
        fixture.snapshot = PlayerSnapshot(current = track(), hasActiveTrack = true)
        fixture.recordCommand()
        fixture.ensurePlayerView()
        advanceUntilIdle()

        fixture.snapshot = PlayerSnapshot()
        fixture.emit(PlayerEvent.Stopped)
        advanceUntilIdle()

        assertEquals(PlayerSnapshot(), fixture.viewSnapshots.last())
        assertEquals(1, fixture.playerChannel.editedData.size)
    }

    @Test
    fun `Given an idle empty queue When no action runs Then create the player channel with the idle embed`() =
        coordinatorTest { fixture ->
            fixture.recordCommand()

            val reply = fixture.ensurePlayerView()
            advanceUntilIdle()

            assertEquals(english(PlayerMessageKeys.PLAYER_CHANNEL_CREATED, PLAYER_CHANNEL_ID), reply)
            assertEquals(listOf(PlayerSnapshot()), fixture.viewSnapshots)
            coVerify(exactly = 1) { fixture.channelService.setUp(fixture.guild, fixture.view) }
            assertTrue(
                fixture.commandChannel.sentTexts.isEmpty(),
                "the status line is the command reply, not a second chat message",
            )
        }

    @Test
    fun `Given an active track When no action runs Then create the player channel with the current snapshot`() =
        coordinatorTest { fixture ->
            val snapshot = PlayerSnapshot(current = track(), hasActiveTrack = true)
            fixture.snapshot = snapshot
            fixture.recordCommand()

            val reply = fixture.ensurePlayerView()
            advanceUntilIdle()

            assertEquals(english(PlayerMessageKeys.PLAYER_CHANNEL_CREATED, PLAYER_CHANNEL_ID), reply)
            assertEquals(listOf(snapshot), fixture.viewSnapshots)
            coVerify(exactly = 1) { fixture.channelService.setUp(fixture.guild, fixture.view) }
        }

    @Test
    fun `Given a reused player channel When no action runs Then reply the existing channel status`() =
        coordinatorTest { fixture ->
            fixture.channelServiceResult = fixture.channelResult(isNewChannel = false)
            fixture.snapshot = PlayerSnapshot(current = track(), hasActiveTrack = true)
            fixture.recordCommand()

            val reply = fixture.ensurePlayerView()
            advanceUntilIdle()

            assertEquals(english(PlayerMessageKeys.PLAYER_SHOWN_ON_EXISTING_CHANNEL, PLAYER_CHANNEL_ID), reply)
            assertTrue(
                fixture.commandChannel.sentTexts.isEmpty(),
                "the status line is the command reply, not a second chat message",
            )
            coVerify(exactly = 1) { fixture.channelService.setUp(fixture.guild, fixture.view) }
        }

    @Test
    fun `Given a player channel that cannot be created When no action runs Then fall back to the invoking channel`() =
        coordinatorTest { fixture ->
            fixture.channelServiceResult = null
            fixture.snapshot = PlayerSnapshot(current = track(), hasActiveTrack = true)
            fixture.recordCommand()

            val reply = fixture.ensurePlayerView()
            advanceUntilIdle()

            val fallback = fixture.commandChannel.sentData.single()
            assertEquals(english(PlayerMessageKeys.PLAYER_CHANNEL_CREATION_FAILED), reply)
            assertEquals(english(PlayerMessageKeys.PLAYER_CHANNEL_CREATION_FAILED), fallback.content)
            assertEquals(1, fallback.embeds.size)
            assertTrue(fixture.playerChannel.sentData.isEmpty())
        }

    @Test
    fun `Given a channel service that throws When no action runs Then fall back without leaking the failure`() =
        coordinatorTest { fixture ->
            coEvery { fixture.channelService.setUp(any(), any()) } throws IllegalStateException("rest boom")
            fixture.recordCommand()

            val reply = fixture.ensurePlayerView()
            advanceUntilIdle()

            assertEquals(english(PlayerMessageKeys.PLAYER_CHANNEL_CREATION_FAILED), reply)
            assertEquals(
                english(PlayerMessageKeys.PLAYER_CHANNEL_CREATION_FAILED),
                fixture.commandChannel.sentTexts.single(),
                "a failed view construction falls back to a plain warning in the invoking channel",
            )
            assertTrue(fixture.commandChannel.sentData.isEmpty(), "no stale embed may be reused after the failure")
        }

    @Test
    fun `Given a recorded voice channel When a command records no name Then keep the previous name`() =
        coordinatorTest { fixture ->
            fixture.snapshot = PlayerSnapshot(current = track(), hasActiveTrack = true)
            fixture.recordCommand(channelName = "General")
            fixture.coordinator.recordCommand(
                channelId = ChannelId(COMMAND_CHANNEL_ID.toULong()),
                guildId = GuildId(GUILD_ID.toULong()),
                guildLocale = null,
            )

            fixture.emit(PlayerEvent.TrackStarted(track()))
            advanceUntilIdle()

            assertEquals("🎶 Playing Artist - Song in General", fixture.commandChannel.sentTexts.single())
        }

    @Test
    fun `Given a live player embed When the embed update is cancelled Then the cancellation escapes and keeps the ids`() =
        coordinatorTest { fixture ->
            fixture.snapshot = PlayerSnapshot(current = track(), hasActiveTrack = true)
            fixture.recordCommand()
            fixture.ensurePlayerView()
            advanceUntilIdle()
            val announcements = fixture.commandChannel.sentTexts.toList()

            fixture.viewCreationFailure = CancellationException("coordinator cancelled")
            fixture.snapshot = PlayerSnapshot(current = track(title = "Next"), hasActiveTrack = true)
            fixture.emit(PlayerEvent.QueueChanged(fixture.snapshot))
            advanceUntilIdle()
            // The rethrow cancels the serial event loop, so nothing queued after the cancellation is handled.
            fixture.emit(PlayerEvent.NoMatches("after the cancellation"))
            advanceUntilIdle()

            assertEquals(announcements, fixture.commandChannel.sentTexts, "a cancellation must not be announced")
            assertTrue(fixture.playerChannel.editedData.isEmpty(), "the cancelled update must never reach the port")
            coVerify(exactly = 1) { fixture.channelService.setUp(fixture.guild, fixture.view) }
            assertEquals(
                ChannelId(PLAYER_CHANNEL_ID.toULong()),
                fixture.playerChannelId(),
                "a cancellation must keep the player channel id instead of resetting it like a failure",
            )
            assertEquals(
                fixture.messageId,
                fixture.playerMessageId(),
                "a cancellation must keep the player message id instead of resetting it like a failure",
            )
        }

    @Test
    fun `Given an idle queue When the leave timer expires Then stop and disconnect once`() = coordinatorTest { fixture ->
        fixture.snapshot = PlayerSnapshot()
        fixture.recordCommand()

        fixture.emit(PlayerEvent.TrackEnded(queueEmpty = true))
        advanceTimeBy(9_999L)
        runCurrent()
        assertEquals(0, fixture.leaveCalls, "the leave timer must wait the full delay")

        advanceTimeBy(2L)
        runCurrent()

        assertEquals(1, fixture.leaveCalls)
    }

    @Test
    fun `Given a track that starts within the leave window When the timer expires Then stay connected`() =
        coordinatorTest { fixture ->
            fixture.snapshot = PlayerSnapshot()
            fixture.recordCommand()
            fixture.emit(PlayerEvent.TrackEnded(queueEmpty = true))
            runCurrent()

            fixture.snapshot = PlayerSnapshot(current = track(), hasActiveTrack = true)
            fixture.emit(PlayerEvent.TrackStarted(track()))
            advanceTimeBy(10_001L)
            runCurrent()

            assertEquals(0, fixture.leaveCalls)
        }

    @Test
    fun `Given a queued track playlist started track or stop When the leave timer was scheduled Then cancel it`() =
        coordinatorTest { fixture ->
            fixture.snapshot = PlayerSnapshot()
            fixture.recordCommand()
            val cancellingEvents = listOf(
                PlayerEvent.TrackQueued(track(), addToFront = false, uri = null),
                PlayerEvent.PlaylistQueued("Mix", trackCount = 1, playlistUrl = null, addToFront = false),
                PlayerEvent.TrackStarted(track()),
                PlayerEvent.Stopped,
            )

            cancellingEvents.forEach { event ->
                fixture.emit(PlayerEvent.TrackEnded(queueEmpty = true))
                runCurrent()
                fixture.emit(event)
                advanceTimeBy(10_001L)
                runCurrent()
            }

            assertEquals(0, fixture.leaveCalls, "every player event must cancel the leave timer")
        }

    @Test
    fun `Given a new play When the leave timer was scheduled Then cancel it`() = coordinatorTest { fixture ->
        fixture.snapshot = PlayerSnapshot()
        fixture.recordCommand()
        fixture.emit(PlayerEvent.TrackEnded(queueEmpty = true))
        runCurrent()

        fixture.startPlayback("next")
        advanceTimeBy(10_001L)
        runCurrent()

        assertEquals(0, fixture.leaveCalls, "a new play must cancel the leave timer")
    }

    @Test
    fun `Given a leave timer being installed When a track is queued immediately Then the leave never fires`() =
        coordinatorTest { fixture ->
            fixture.snapshot = PlayerSnapshot()
            fixture.recordCommand()

            // No virtual-time gap: the queue event arrives while the timer job is being installed.
            fixture.emit(PlayerEvent.TrackEnded(queueEmpty = true))
            fixture.emit(PlayerEvent.TrackQueued(track(), addToFront = false, uri = null))
            advanceTimeBy(10_001L)
            runCurrent()

            assertEquals(
                0,
                fixture.leaveCalls,
                "a timer reference installed before launch must be cancellable by the next event",
            )
        }

    @Test
    fun `Given a non-idle snapshot or queue When the leave timer expires Then re-check before leaving`() =
        coordinatorTest { fixture ->
            fixture.recordCommand()
            fixture.snapshot = PlayerSnapshot(current = track(), hasActiveTrack = true)
            fixture.emit(PlayerEvent.TrackEnded(queueEmpty = true))
            advanceTimeBy(10_001L)
            runCurrent()

            fixture.snapshot = PlayerSnapshot(queue = listOf(track()))
            fixture.emit(PlayerEvent.TrackEnded(queueEmpty = true))
            advanceTimeBy(10_001L)
            runCurrent()

            assertEquals(0, fixture.leaveCalls, "an active track or a queued track must keep the bot connected")
        }

    @Test
    fun `Given a load that never starts When playback is requested Then announce the timeout`() = coordinatorTest { fixture ->
        fixture.recordCommand()

        fixture.startPlayback("track")
        advanceUntilIdle()

        assertEquals(listOf(english(PlayerMessageKeys.PLAYER_LOAD_TIMEOUT)), fixture.commandChannel.sentTexts)
        coVerify(exactly = 0) { fixture.adapter.connect(any()) }
    }

    @Test
    fun `Given a rejected load When playback is requested Then leave the event message and never time out`() =
        coordinatorTest { fixture ->
            coEvery { fixture.engine.loadAndPlay("bad") } coAnswers {
                fixture.presentation.value = JdaPlayerPresentation(
                    revision = 1,
                    lastEvent = PlayerEvent.LoadFailed("provider raw text"),
                )
                fixture.events.tryEmit(PlayerEvent.LoadFailed("provider raw text"))
            }
            fixture.recordCommand()

            fixture.startPlayback("bad")
            advanceUntilIdle()

            val message = fixture.commandChannel.sentTexts.single()
            assertEquals(
                english(PlayerMessageKeys.LOAD_FAILED, english(PlayerMessageKeys.PLAYER_LOAD_REJECTED)),
                message,
            )
            assertFalse(fixture.commandChannel.sentTexts.contains(english(PlayerMessageKeys.PLAYER_LOAD_TIMEOUT)))
            coVerify(exactly = 0) { fixture.adapter.connect(any()) }
        }

    @Test
    fun `Given a started track whose voice session is refused When connecting Then announce the refusal`() =
        coordinatorTest { fixture ->
            coEvery { fixture.engine.loadAndPlay("track") } coAnswers {
                fixture.presentation.value = JdaPlayerPresentation(revision = 1, activeTrack = true)
            }
            coEvery { fixture.adapter.connect(fixture.voiceChannel) } returns false
            fixture.recordCommand()

            fixture.startPlayback("track")
            advanceUntilIdle()

            assertEquals(
                listOf(english(PlayerMessageKeys.PLAYER_VOICE_SESSION_REFUSED)),
                fixture.commandChannel.sentTexts,
            )
        }

    @Test
    fun `Given a started track whose connection throws When connecting Then announce the safe failure`() =
        coordinatorTest { fixture ->
            coEvery { fixture.engine.loadAndPlay("track") } coAnswers {
                fixture.presentation.value = JdaPlayerPresentation(revision = 1, activeTrack = true)
            }
            coEvery { fixture.adapter.connect(fixture.voiceChannel) } throws IllegalStateException("gateway boom")
            fixture.recordCommand()

            fixture.startPlayback("track")
            advanceUntilIdle()

            assertEquals(
                listOf(english(PlayerMessageKeys.PLAYER_VOICE_CONNECTION_FAILED)),
                fixture.commandChannel.sentTexts,
            )
        }

    @Test
    fun `Given an already active track When a new load is requested Then connect without waiting for a start`() =
        coordinatorTest { fixture ->
            fixture.presentation.value = JdaPlayerPresentation(revision = 3, activeTrack = true)
            fixture.recordCommand()

            fixture.startPlayback("track")
            advanceUntilIdle()

            coVerify(exactly = 1) { fixture.adapter.connect(fixture.voiceChannel) }
            assertTrue(fixture.commandChannel.sentTexts.isEmpty())
        }

    private fun coordinatorTest(block: suspend TestScope.(CoordinatorFixture) -> Unit) = runTest {
        val fixture = CoordinatorFixture(this)
        runCurrent()
        try {
            block(fixture)
        } finally {
            fixture.close()
        }
    }

    private suspend fun english(key: String, vararg arguments: Any): String =
        if (arguments.isEmpty()) {
            expected.getString(key = key)
        } else {
            expected.getStringFormat(key = key, arguments = arguments)
        }

    private fun track(title: String = "Song"): PlayerTrack = PlayerTrack(
        identifier = title,
        title = title,
        author = "Artist",
    )

    private inner class CoordinatorFixture(
        private val scope: TestScope,
    ) : AutoCloseable {

        val commandChannel = RecordingChannel(COMMAND_CHANNEL_ID)
        val playerChannel = RecordingChannel(PLAYER_CHANNEL_ID)
        val events = MutableSharedFlow<PlayerEvent>(extraBufferCapacity = 64)
        val presentation = MutableStateFlow(JdaPlayerPresentation())
        val jda: JDA = mockk {
            every { getTextChannelById(COMMAND_CHANNEL_ID) } returns commandChannel.channel
            every { getTextChannelById(PLAYER_CHANNEL_ID) } returns playerChannel.channel
        }
        val guild: Guild = mockk {
            every { idLong } returns GUILD_ID
            every { name } returns "Jungle"
            every { jda } returns this@CoordinatorFixture.jda
        }
        val engine: PlayerEngine = mockk {
            every { snapshot() } answers { this@CoordinatorFixture.snapshot }
        }
        val adapter: JdaGuildPlayerVoiceAdapter = mockk {
            every { events } returns this@CoordinatorFixture.events
            every { presentation } returns this@CoordinatorFixture.presentation
        }
        val channelService: JdaPlayerChannelService = mockk()
        val viewFactory: PlayerMessageViewFactory = mockk()
        val viewSnapshots = mutableListOf<PlayerSnapshot>()
        val voiceChannel: VoiceChannel = mockk {
            every { name } returns "General"
        }
        val messageId = PlayerMessageId(MESSAGE_ID.toULong())

        var snapshot = PlayerSnapshot()
        var leaveCalls = 0
            private set

        var viewCreationFailure: Throwable? = null

        var channelServiceResult: JdaPlayerChannelResult? = channelResult(isNewChannel = true)
        val view = MessageView(embed = EmbedView(title = "player-view"))

        val coordinator = JdaPlayerMessageCoordinator(
            guild = guild,
            engine = engine,
            adapter = adapter,
            localizationService = expected,
            channelService = channelService,
            viewFactory = viewFactory,
            scope = scope,
            onLeave = { leaveCalls++ },
        )

        init {
            coJustRun { engine.loadAndPlay(any()) }
            coEvery { adapter.connect(any()) } returns true
            coEvery { channelService.setUp(any(), any()) } answers { channelServiceResult }
            coEvery { viewFactory.createPlayerView(any(), any(), any(), any()) } coAnswers {
                viewCreationFailure?.let { throw it }
                viewSnapshots += secondArg<PlayerSnapshot>()
                view
            }
            every { voiceChannel.idLong } returns VOICE_CHANNEL_ID
            every { jda.getVoiceChannelById(VOICE_CHANNEL_ID) } returns voiceChannel
        }

        fun recordCommand(channelName: String = "General") {
            coordinator.recordCommand(
                channelId = ChannelId(COMMAND_CHANNEL_ID.toULong()),
                guildId = GuildId(GUILD_ID.toULong()),
                guildLocale = null,
                channelName = channelName,
            )
        }

        fun emit(event: PlayerEvent) {
            events.tryEmit(event)
        }

        fun startPlayback(input: String) {
            coordinator.startPlayback(voiceChannel, input)
        }

        suspend fun ensurePlayerView(): String = coordinator.ensurePlayerView()

        fun playerChannelId(): ChannelId? = privateField("playerChannelId") as? ChannelId

        fun playerMessageId(): PlayerMessageId? = privateField("playerMessageId") as? PlayerMessageId

        private fun privateField(name: String): Any? = JdaPlayerMessageCoordinator::class.java
            .getDeclaredField(name)
            .apply { isAccessible = true }
            .get(coordinator)

        fun channelResult(isNewChannel: Boolean): JdaPlayerChannelResult = JdaPlayerChannelResult(
            channelId = ChannelId(PLAYER_CHANNEL_ID.toULong()),
            messageId = messageId,
            isNewChannel = isNewChannel,
        )

        override fun close() {
            coordinator.close()
        }
    }

    private class RecordingChannel(val id: Long) {

        val sentTexts = mutableListOf<String>()
        val sentData = mutableListOf<MessageCreateData>()
        val editedTexts = mutableListOf<String>()
        val editedData = mutableListOf<MessageEditData>()

        private val message = mockk<Message> {
            every { idLong } returns 900L
        }
        private val createAction = mockk<MessageCreateAction> {
            every { queue(any(), any()) } answers { firstArg<Consumer<Message>>().accept(message) }
        }
        private val editAction = mockk<MessageEditAction> {
            every { queue(any(), any()) } answers { firstArg<Consumer<Message?>>().accept(null) }
        }

        val channel: TextChannel = mockk {
            every { idLong } returns this@RecordingChannel.id
            every { sendMessage(any<String>()) } answers {
                sentTexts += firstArg<String>()
                createAction
            }
            every { sendMessage(any<MessageCreateData>()) } answers {
                sentData += firstArg<MessageCreateData>()
                createAction
            }
            every { editMessageById(any<Long>(), any<String>()) } answers {
                editedTexts += secondArg<CharSequence>().toString()
                editAction
            }
            every { editMessageById(any<Long>(), any<MessageEditData>()) } answers {
                editedData += secondArg<MessageEditData>()
                editAction
            }
        }
    }

    private class EmptyLocaleRepository : LocaleRepository {

        override suspend fun getGuildLocale(guildId: GuildId): LocaleCode? = null

        override suspend fun setGuildLocale(guildId: GuildId, locale: LocaleCode) =
            error("the coordinator must not persist a guild locale")

        override suspend fun removeGuildLocale(guildId: GuildId) =
            error("the coordinator must not persist a guild locale")

        override suspend fun getAllGuildLocales(): Map<GuildId, LocaleCode> = emptyMap()
    }

    private companion object {
        private const val GUILD_ID = 42L
        private const val COMMAND_CHANNEL_ID = 43L
        private const val PLAYER_CHANNEL_ID = 77L
        private const val VOICE_CHANNEL_ID = 99L
        private const val MESSAGE_ID = 900L

        private val expected = GuildLocalizationService(
            getGuildLocaleUseCase = GetGuildLocaleUseCase(EmptyLocaleRepository()),
            catalog = LocalizationCatalog.load(),
        )
    }
}
