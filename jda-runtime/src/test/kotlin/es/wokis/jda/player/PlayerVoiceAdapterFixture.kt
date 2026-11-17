package es.wokis.jda.player

import es.wokis.player.engine.PlayerEngine
import es.wokis.player.engine.audio.ContinuousPlayerAudioStream
import es.wokis.player.engine.audio.PlayerAudioState
import es.wokis.player.engine.event.PlayerEvent
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import net.dv8tion.jda.api.audio.AudioSendHandler
import net.dv8tion.jda.api.audio.hooks.ConnectionStatus
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel
import net.dv8tion.jda.api.managers.AudioManager
import org.junit.jupiter.api.AfterEach
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Strict-mock fixture shared by the voice-adapter test classes. One adapter, one strict engine and
 * one strict [AudioManager] per fixture; no global mocking and no relaxed answers.
 */
internal class PlayerVoiceAdapterFixture(
    dispatcher: CoroutineDispatcher? = null,
    initiallyConnected: Boolean = false,
    private val serialDispatcherOverride: CoroutineDispatcher? = null,
) : AutoCloseable {

    val events = MutableSharedFlow<PlayerEvent>(extraBufferCapacity = 4)
    val stream = FakePlayerAudioStream()
    val streams = CopyOnWriteArrayList<FakePlayerAudioStream>()
    private var streamCalls = 0

    @Volatile
    private var connected = initiallyConnected

    @Volatile
    private var connectionStatus = if (initiallyConnected) {
        ConnectionStatus.CONNECTED
    } else {
        ConnectionStatus.NOT_CONNECTED
    }

    val engine: PlayerEngine = mockk()
    val audioManager: AudioManager = mockk()
    val guild: Guild = mockk()
    val channel: VoiceChannel = mockk {
        every { idLong } returns 42L
    }
    val otherChannel: VoiceChannel = mockk {
        every { idLong } returns 43L
    }
    val scope = CoroutineScope(SupervisorJob() + (dispatcher ?: Dispatchers.Unconfined))
    val order = CopyOnWriteArrayList<String>()
    private val openedChannels = CopyOnWriteArrayList<Long>()
    var installedHandler: AudioSendHandler? = null
    private val handlerSlot = slot<AudioSendHandler>()
    var disconnectConfirmation: (suspend () -> Unit)? = null

    /** Models JDA's async close: the connection keeps reporting CONNECTED until the test confirms. */
    @Volatile
    var deferDisconnect = false

    val adapter: JdaGuildPlayerVoiceAdapter by lazy {
        when {
            disconnectConfirmation != null -> JdaGuildPlayerVoiceAdapter(
                guild = guild,
                engine = engine,
                scope = scope,
                serialDispatcher = serialDispatcherOverride ?: dispatcher ?: Dispatchers.Unconfined,
                disconnectConfirmation = requireNotNull(disconnectConfirmation),
            )

            serialDispatcherOverride != null ->
                JdaGuildPlayerVoiceAdapter(guild, engine, scope, serialDispatcherOverride)

            dispatcher != null ->
                JdaGuildPlayerVoiceAdapter(guild, engine, scope, dispatcher)

            else -> JdaGuildPlayerVoiceAdapter(guild, engine, scope)
        }
    }

    init {
        every { engine.events } returns events
        every { engine.audioStream() } answers {
            val next = if (streamCalls++ == 0) stream else FakePlayerAudioStream()
            streams += next
            next
        }
        every { guild.audioManager } returns audioManager
        every { audioManager.setSendingHandler(capture(handlerSlot)) } answers {
            installedHandler = handlerSlot.captured
        }
        every { audioManager.setSendingHandler(null) } answers {
            installedHandler = null
        }
        every { audioManager.isConnected } answers { connected }
        every { audioManager.connectionStatus } answers { connectionStatus }
        every { audioManager.openAudioConnection(channel) } answers {
            openedChannels += channel.idLong
            order += "open"
            markConnected()
        }
        every { audioManager.openAudioConnection(otherChannel) } answers {
            openedChannels += otherChannel.idLong
            order += "open"
            markConnected()
        }
        every { audioManager.closeAudioConnection() } answers {
            order += "close"
            if (!deferDisconnect) {
                markDisconnected()
            }
        }
    }

    fun markConnected() {
        connected = true
        connectionStatus = ConnectionStatus.CONNECTED
    }

    fun markDisconnected() {
        connected = false
        connectionStatus = ConnectionStatus.NOT_CONNECTED
    }

    /** Models an in-flight handshake or teardown: JDA reports a status while isConnected is false. */
    fun markConnectionStatus(status: ConnectionStatus) {
        connected = false
        connectionStatus = status
    }

    fun currentConnectionStatus(): ConnectionStatus = connectionStatus

    /** Number of [AudioManager.openAudioConnection] calls recorded for [target]. */
    fun openCount(target: VoiceChannel): Int = openedChannels.count { it == target.idLong }

    override fun close() {
        scope.cancel()
    }

    fun dispose() {
        close()
    }
}

internal class FakePlayerAudioStream : ContinuousPlayerAudioStream {
    var nextState: PlayerAudioState = PlayerAudioState.PausedSilence
    var closeCalls = 0
        private set
    var failOnClose = false

    override fun next(): PlayerAudioState = nextState

    override fun close() {
        closeCalls++
        if (failOnClose) {
            error("stream close failed")
        }
    }
}

/** Disposes every fixture created by a test, even when an assertion fails. */
internal abstract class PlayerVoiceAdapterTestSupport {

    private val fixtures = mutableListOf<PlayerVoiceAdapterFixture>()

    @AfterEach
    fun disposeFixtures() {
        fixtures.forEach { it.dispose() }
        fixtures.clear()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    protected fun TestScope.voiceFixture(
        initiallyConnected: Boolean = false,
        serialDispatcherOverride: CoroutineDispatcher? = null,
    ): PlayerVoiceAdapterFixture = PlayerVoiceAdapterFixture(
        dispatcher = UnconfinedTestDispatcher(testScheduler),
        initiallyConnected = initiallyConnected,
        serialDispatcherOverride = serialDispatcherOverride,
    ).also { fixtures += it }

    /**
     * Real per-guild serial dispatcher (`limitedParallelism(1)`), while the adapter scope stays
     * Unconfined; used only by the real-thread race test.
     */
    protected fun realThreadVoiceFixture(): PlayerVoiceAdapterFixture =
        PlayerVoiceAdapterFixture().also { fixtures += it }
}
