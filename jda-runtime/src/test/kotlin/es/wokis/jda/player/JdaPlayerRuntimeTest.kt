package es.wokis.jda.player

import es.wokis.commands.player.PlayerMessageCoordinator
import es.wokis.domain.locale.GetGuildLocaleUseCase
import es.wokis.domain.locale.LocaleCode
import es.wokis.domain.locale.LocaleRepository
import es.wokis.domain.model.ChannelId
import es.wokis.domain.model.GuildId
import es.wokis.localization.GuildLocalizationService
import es.wokis.localization.LocalizationCatalog
import es.wokis.player.engine.PlayerEngine
import es.wokis.player.engine.PlayerEngineFactory
import es.wokis.player.engine.PlayerEngines
import es.wokis.player.engine.audio.ContinuousPlayerAudioStream
import es.wokis.player.engine.audio.PlayerAudioState
import es.wokis.player.engine.event.PlayerEvent
import es.wokis.player.engine.model.PlayerSnapshot
import es.wokis.player.engine.model.PlayerTrack
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.audio.AudioSendHandler
import net.dv8tion.jda.api.audio.hooks.ConnectionStatus
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel
import net.dv8tion.jda.api.managers.AudioManager
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction
import net.dv8tion.jda.api.utils.messages.MessageCreateData
import org.junit.jupiter.api.Test
import java.util.function.Consumer
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class JdaPlayerRuntimeTest {

    @Test
    fun `Given an active runtime When close signals Then engines and factory close and new access is rejected`() = runTest {
        val engine: PlayerEngine = mockk {
            every { events } returns emptyFlow()
            justRun { close() }
        }
        val factory: PlayerEngineFactory = mockk {
            every { create() } returns engine
            justRun { close() }
        }
        mockkObject(PlayerEngines)
        every { PlayerEngines.factory(any()) } returns factory
        val guild: Guild = mockk {
            every { idLong } returns 42L
        }
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val runtime = JdaPlayerRuntime(
            scope = scope,
            serialDispatcherFactory = { UnconfinedTestDispatcher(testScheduler) },
        )
        try {
            runtime.engineFor(guild)
            runtime.voiceAdapterFor(guild)

            runtime.close()

            verify(exactly = 1) { engine.close() }
            verify(exactly = 1) { factory.close() }
            assertFailsWith<IllegalStateException> { runtime.engineFor(guild) }
            assertFailsWith<IllegalStateException> { runtime.voiceAdapterFor(guild) }
        } finally {
            scope.cancel()
            unmockkObject(PlayerEngines)
        }
    }

    @Test
    fun `Given two guild adapters When requested repeatedly Then one dispatcher per guild is created and retained`() = runTest {
        val engine: PlayerEngine = mockk {
            every { events } returns emptyFlow()
            justRun { close() }
        }
        val factory: PlayerEngineFactory = mockk {
            every { create() } returns engine
            justRun { close() }
        }
        mockkObject(PlayerEngines)
        every { PlayerEngines.factory(any()) } returns factory
        val firstGuild: Guild = mockk {
            every { idLong } returns 41L
        }
        val secondGuild: Guild = mockk {
            every { idLong } returns 42L
        }
        val dispatchers = mutableListOf<CoroutineDispatcher>()
        val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))
        val runtime = JdaPlayerRuntime(
            scope = scope,
            serialDispatcherFactory = {
                UnconfinedTestDispatcher(testScheduler).also { dispatchers += it }
            },
        )
        try {
            val firstAdapter = runtime.voiceAdapterFor(firstGuild)
            val secondAdapter = runtime.voiceAdapterFor(secondGuild)
            val firstAgain = runtime.voiceAdapterFor(firstGuild)
            val secondAgain = runtime.voiceAdapterFor(secondGuild)
            runtime.shutdown()

            assertSame(firstAdapter, firstAgain)
            assertSame(secondAdapter, secondAgain)
            assertEquals(2, dispatchers.size)
            assertNotSame(dispatchers[0], dispatchers[1])
        } finally {
            scope.cancel()
            unmockkObject(PlayerEngines)
        }
    }

    @Test
    fun `Given a fast close When shutdown follows Then awaited cleanup remains callable`() = runTest {
        val engine: PlayerEngine = mockk {
            every { events } returns emptyFlow()
            justRun { close() }
        }
        val factory: PlayerEngineFactory = mockk {
            every { create() } returns engine
            justRun { close() }
        }
        mockkObject(PlayerEngines)
        every { PlayerEngines.factory(any()) } returns factory
        val guild: Guild = mockk {
            every { idLong } returns 42L
        }
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val runtime = JdaPlayerRuntime(
            scope = scope,
            serialDispatcherFactory = { UnconfinedTestDispatcher(testScheduler) },
        )
        try {
            runtime.engineFor(guild)
            runtime.voiceAdapterFor(guild)

            runtime.close()
            assertFailsWith<IllegalStateException> { runtime.engineFor(guild) }
            runtime.shutdown()

            verify(exactly = 1) { engine.close() }
            verify(atLeast = 1) { factory.close() }
        } finally {
            scope.cancel()
            unmockkObject(PlayerEngines)
        }
    }

    @Test
    fun `Given engine and factory close failures When shutdown runs Then both failures are reported`() = runTest {
        val engineFailure = IllegalStateException("engine close failed")
        val factoryFailure = IllegalStateException("factory close failed")
        val engine: PlayerEngine = mockk {
            every { events } returns emptyFlow()
            every { close() } throws engineFailure
        }
        val factory: PlayerEngineFactory = mockk {
            every { create() } returns engine
            every { close() } throws factoryFailure
        }
        mockkObject(PlayerEngines)
        every { PlayerEngines.factory(any()) } returns factory
        val guild: Guild = mockk {
            every { idLong } returns 42L
        }
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val runtime = JdaPlayerRuntime(
            scope = scope,
            serialDispatcherFactory = { UnconfinedTestDispatcher(testScheduler) },
        )
        try {
            runtime.engineFor(guild)

            val thrown = assertFailsWith<IllegalStateException> {
                runtime.shutdown()
            }

            assertSame(engineFailure, thrown)
            assertEquals(listOf(factoryFailure), thrown.suppressed.toList())
            verify(exactly = 1) { factory.close() }
        } finally {
            scope.cancel()
            unmockkObject(PlayerEngines)
        }
    }

    @Test
    fun `Given a guild requested twice When shutdown runs twice Then engines and the shared factory close once`() = runTest {
        val engine: PlayerEngine = mockk {
            every { events } returns emptyFlow()
            justRun { close() }
        }
        val factory: PlayerEngineFactory = mockk {
            every { create() } returns engine
            justRun { close() }
        }
        mockkObject(PlayerEngines)
        every { PlayerEngines.factory(any()) } returns factory
        val guild: Guild = mockk {
            every { idLong } returns 42L
        }
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val runtime = JdaPlayerRuntime(
            scope = scope,
            serialDispatcherFactory = { UnconfinedTestDispatcher(testScheduler) },
        )
        try {
            val firstEngine = runtime.engineFor(guild)
            val secondEngine = runtime.engineFor(guild)
            val firstAdapter = runtime.voiceAdapterFor(guild)
            val secondAdapter = runtime.voiceAdapterFor(guild)
            runtime.shutdown()
            runtime.shutdown()

            assertSame(firstEngine, secondEngine)
            assertSame(firstAdapter, secondAdapter)
            verify(exactly = 1) { factory.create() }
            verify(exactly = 1) { engine.close() }
            verify(exactly = 1) { factory.close() }
        } finally {
            scope.cancel()
            unmockkObject(PlayerEngines)
        }
    }

    @Test
    fun `Given a connected guild player When runtime reconnect runs Then delegate to the adapter and reopen`() = runTest {
        val firstStream = FakeRuntimeStream()
        val secondStream = FakeRuntimeStream()
        var streamCalls = 0
        val engine: PlayerEngine = mockk {
            every { events } returns emptyFlow()
            every { audioStream() } answers {
                if (streamCalls++ == 0) firstStream else secondStream
            }
            justRun { close() }
        }
        val factory: PlayerEngineFactory = mockk {
            every { create() } returns engine
            justRun { close() }
        }
        mockkObject(PlayerEngines)
        every { PlayerEngines.factory(any()) } returns factory
        var connected = false
        val audioManager: AudioManager = mockk()
        val channel: VoiceChannel = mockk {
            every { idLong } returns 42L
        }
        val guild: Guild = mockk {
            every { idLong } returns 42L
        }
        every { guild.audioManager } returns audioManager
        every { audioManager.isConnected } answers { connected }
        every { audioManager.connectionStatus } answers {
            if (connected) ConnectionStatus.CONNECTED else ConnectionStatus.NOT_CONNECTED
        }
        every { audioManager.setSendingHandler(any<AudioSendHandler>()) } returns Unit
        every { audioManager.setSendingHandler(null) } returns Unit
        every { audioManager.openAudioConnection(channel) } answers { connected = true }
        every { audioManager.closeAudioConnection() } answers { connected = false }
        val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))
        val runtime = JdaPlayerRuntime(
            scope = scope,
            serialDispatcherFactory = { UnconfinedTestDispatcher(testScheduler) },
        )
        try {
            assertTrue(runtime.connect(guild, channel), "the initial connect must open a session")

            val result = runtime.reconnect(guild, channel)

            assertEquals(JdaPlayerReconnectResult.Reconnected, result)
            verify(exactly = 2) { audioManager.openAudioConnection(channel) }
            verify(exactly = 1) { audioManager.closeAudioConnection() }
            assertTrue(firstStream.closeCalls > 0, "the first stream must be closed before reopening")
        } finally {
            scope.cancel()
            unmockkObject(PlayerEngines)
        }
    }

    @Test
    fun `Given one guild When messages are requested twice Then the same coordinator is created once`() = runTest {
        val engine: PlayerEngine = mockk {
            every { events } returns emptyFlow()
            justRun { close() }
        }
        val factory: PlayerEngineFactory = mockk {
            every { create() } returns engine
            justRun { close() }
        }
        mockkObject(PlayerEngines)
        every { PlayerEngines.factory(any()) } returns factory
        val jdaMock: JDA = mockk()
        val guild: Guild = mockk {
            every { idLong } returns GUILD_ID
            every { jda } returns jdaMock
        }
        val dispatchers = mutableListOf<CoroutineDispatcher>()
        val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))
        val runtime = testRuntime(scope) {
            UnconfinedTestDispatcher(testScheduler).also { dispatchers += it }
        }
        try {
            val first = runtime.messagesFor(guild)
            val second = runtime.messagesFor(guild)
            runtime.shutdown()

            assertSame(first, second)
            verify(exactly = 1) { factory.create() }
            assertEquals(1, dispatchers.size, "one coordinator and one adapter must share one serial dispatcher")
        } finally {
            scope.cancel()
            unmockkObject(PlayerEngines)
        }
    }

    @Test
    fun `Given a live coordinator When shutdown runs Then the fan-out completes and no further messages are sent`() =
        runTest {
            val commandChannel = RecordingRuntimeChannel(COMMAND_CHANNEL_ID)
            val eventFlow = MutableSharedFlow<PlayerEvent>(extraBufferCapacity = 8)
            val engine: PlayerEngine = mockk {
                every { events } returns eventFlow
                every { snapshot() } returns PlayerSnapshot()
                justRun { close() }
            }
            val factory: PlayerEngineFactory = mockk {
                every { create() } returns engine
                justRun { close() }
            }
            mockkObject(PlayerEngines)
            every { PlayerEngines.factory(any()) } returns factory
            val jdaMock: JDA = mockk {
                every { getTextChannelById(COMMAND_CHANNEL_ID) } returns commandChannel.channel
            }
            val guild: Guild = mockk {
                every { idLong } returns GUILD_ID
                every { jda } returns jdaMock
            }
            val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))
            val runtime = testRuntime(scope) { UnconfinedTestDispatcher(testScheduler) }
            try {
                val closed = CompletableDeferred<Unit>()
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                    runtime.voiceAdapterFor(guild).events.collect { }
                    closed.complete(Unit)
                }
                runCurrent()
                runtime.messagesFor(guild).recordTestCommand()
                eventFlow.emit(queuedEvent())
                advanceUntilIdle()
                assertEquals(1, commandChannel.sentTexts.size)

                runtime.shutdown()
                advanceUntilIdle()
                eventFlow.emit(queuedEvent())
                advanceUntilIdle()

                assertTrue(closed.isCompleted, "the adapter fan-out must complete when the runtime shuts down")
                assertEquals(1, commandChannel.sentTexts.size, "a closed coordinator must not send another message")
                assertFailsWith<IllegalStateException> { runtime.messagesFor(guild) }
            } finally {
                scope.cancel()
                unmockkObject(PlayerEngines)
            }
        }

    @Test
    fun `Given a connected guild player When the leave timer expires Then stop the engine and disconnect`() = runTest {
        val eventFlow = MutableSharedFlow<PlayerEvent>(extraBufferCapacity = 8)
        val stream = FakeRuntimeStream()
        val engine: PlayerEngine = mockk {
            every { events } returns eventFlow
            every { snapshot() } returns PlayerSnapshot()
            every { audioStream() } returns stream
            coJustRun { stop() }
            justRun { close() }
        }
        val factory: PlayerEngineFactory = mockk {
            every { create() } returns engine
            justRun { close() }
        }
        mockkObject(PlayerEngines)
        every { PlayerEngines.factory(any()) } returns factory
        var connected = false
        val audioManager: AudioManager = mockk()
        val channel: VoiceChannel = mockk {
            every { idLong } returns GUILD_ID
        }
        val jdaMock: JDA = mockk()
        val guild: Guild = mockk {
            every { idLong } returns GUILD_ID
            every { jda } returns jdaMock
        }
        every { guild.audioManager } returns audioManager
        every { audioManager.isConnected } answers { connected }
        every { audioManager.connectionStatus } answers {
            if (connected) ConnectionStatus.CONNECTED else ConnectionStatus.NOT_CONNECTED
        }
        every { audioManager.setSendingHandler(any<AudioSendHandler>()) } returns Unit
        every { audioManager.setSendingHandler(null) } returns Unit
        every { audioManager.openAudioConnection(channel) } answers { connected = true }
        every { audioManager.closeAudioConnection() } answers { connected = false }
        val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))
        val runtime = testRuntime(scope) { UnconfinedTestDispatcher(testScheduler) }
        try {
            assertTrue(runtime.connect(guild, channel), "the fixture must own a live voice session")
            val coordinator = runtime.messagesFor(guild)
            coordinator.recordTestCommand()

            eventFlow.emit(PlayerEvent.TrackEnded(queueEmpty = true))
            advanceTimeBy(10_001L)
            runCurrent()

            coVerify(exactly = 1) { engine.stop() }
            verify(exactly = 1) { audioManager.closeAudioConnection() }
        } finally {
            scope.cancel()
            unmockkObject(PlayerEngines)
        }
    }

    private fun testRuntime(
        scope: CoroutineScope,
        serialDispatcherFactory: () -> CoroutineDispatcher,
    ): JdaPlayerRuntime = JdaPlayerRuntime(
        scope = scope,
        serialDispatcherFactory = serialDispatcherFactory,
        localizationService = GuildLocalizationService(
            getGuildLocaleUseCase = GetGuildLocaleUseCase(EmptyLocaleRepository()),
            catalog = LocalizationCatalog.load(),
        ),
    )

    private fun PlayerMessageCoordinator.recordTestCommand() {
        recordCommand(
            channelId = ChannelId(COMMAND_CHANNEL_ID.toULong()),
            guildId = GuildId(GUILD_ID.toULong()),
            guildLocale = null,
            channelName = "General",
        )
    }

    private fun queuedEvent(): PlayerEvent.TrackQueued = PlayerEvent.TrackQueued(
        track = PlayerTrack(identifier = "id", title = "Song", author = "Artist"),
        addToFront = false,
        uri = null,
    )

    private class RecordingRuntimeChannel(private val id: Long) {

        val sentTexts = mutableListOf<String>()

        private val message = mockk<Message> {
            every { idLong } returns 900L
        }
        private val createAction = mockk<MessageCreateAction> {
            every { queue(any(), any()) } answers { firstArg<Consumer<Message>>().accept(message) }
        }

        val channel: TextChannel = mockk {
            every { idLong } returns this@RecordingRuntimeChannel.id
            every { isObfuscated } returns false
            every { sendMessage(any<String>()) } answers {
                sentTexts += firstArg<String>()
                createAction
            }
            every { sendMessage(any<MessageCreateData>()) } returns createAction
        }
    }

    private class EmptyLocaleRepository : LocaleRepository {

        override suspend fun getGuildLocale(guildId: GuildId): LocaleCode? = null

        override suspend fun setGuildLocale(guildId: GuildId, locale: LocaleCode) =
            error("the runtime must not persist a guild locale")

        override suspend fun removeGuildLocale(guildId: GuildId) =
            error("the runtime must not persist a guild locale")

        override suspend fun getAllGuildLocales(): Map<GuildId, LocaleCode> = emptyMap()
    }

    private class FakeRuntimeStream : ContinuousPlayerAudioStream {
        var closeCalls = 0
            private set

        override fun next(): PlayerAudioState = PlayerAudioState.PausedSilence

        override fun close() {
            closeCalls++
        }
    }

    private companion object {
        private const val GUILD_ID = 42L
        private const val COMMAND_CHANNEL_ID = 43L
    }
}
