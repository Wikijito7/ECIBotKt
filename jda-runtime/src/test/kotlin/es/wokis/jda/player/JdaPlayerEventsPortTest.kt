package es.wokis.jda.player

import es.wokis.commands.player.PlayerEventsPort
import es.wokis.commands.player.PlayerPlaybackState
import es.wokis.commands.player.PlayerVoiceTarget
import es.wokis.domain.model.ChannelId
import es.wokis.player.engine.event.PlayerEvent
import es.wokis.player.engine.model.PlayerTrack
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

/**
 * Adapter contract of [JdaPlayerEventsPort].
 *
 * The adapter keeps the buffered fan-out and the presentation revision; this port must only expose
 * them at neutral types and resolve a connect request's channel by id.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class JdaPlayerEventsPortTest {

    private val guild: Guild = mockk()
    private val events = MutableSharedFlow<PlayerEvent>(extraBufferCapacity = 8)
    private val presentation = MutableStateFlow(JdaPlayerPresentation())
    private val voiceChannel: VoiceChannel = mockk()
    private val adapter: JdaGuildPlayerVoiceAdapter = mockk {
        every { events } returns this@JdaPlayerEventsPortTest.events
        every { presentation } returns this@JdaPlayerEventsPortTest.presentation
    }
    private val port: PlayerEventsPort = JdaPlayerEventsPort(guild, adapter)

    @Test
    fun `Given the adapter fan-out When the port is used Then the same buffered flow is exposed`() = runTest {
        val received = mutableListOf<PlayerEvent>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { port.events.collect { received += it } }
        runCurrent()

        val event = PlayerEvent.NoMatches("unknown")
        events.emit(event)
        runCurrent()

        assertSame(events, port.events)
        assertEquals(listOf(event), received)
        job.cancel()
    }

    @Test
    fun `Given an idle presentation When the state is read Then map it to an idle neutral state`() {
        val idle = PlayerPlaybackState(revision = 0, activeTrack = false, loadFailed = false)

        assertEquals(idle, port.playbackState.value)
        assertFalse(port.playbackState.value.loadFailed)
    }

    @Test
    fun `Given an active presentation When the state is read Then carry the revision and the active flag`() {
        presentation.value = JdaPlayerPresentation(revision = 2, activeTrack = true)

        val active = PlayerPlaybackState(revision = 2, activeTrack = true, loadFailed = false)
        assertEquals(active, port.playbackState.value)
    }

    @Test
    fun `Given a load failure event When the state is read Then report a load failure`() {
        val failures = listOf(
            PlayerEvent.NoMatches("unknown"),
            PlayerEvent.LoadFailed("provider rejected"),
            PlayerEvent.PlaybackFailed("decoder exploded"),
        )

        failures.forEachIndexed { index, event ->
            presentation.value = JdaPlayerPresentation(revision = index + 1L, lastEvent = event)

            assertTrue(port.playbackState.value.loadFailed, event.javaClass.simpleName)
            assertEquals(index + 1L, port.playbackState.value.revision, event.javaClass.simpleName)
        }
    }

    @Test
    fun `Given a non failure event When the state is read Then never report a load failure`() {
        presentation.value = JdaPlayerPresentation(revision = 4, lastEvent = PlayerEvent.TrackStarted(track()))

        assertFalse(port.playbackState.value.loadFailed)
    }

    @Test
    fun `Given a collector When the adapter presentation changes Then the neutral state is emitted`() = runTest {
        val received = mutableListOf<PlayerPlaybackState>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { port.playbackState.collect { received += it } }
        runCurrent()

        presentation.value = JdaPlayerPresentation(revision = 1, activeTrack = true)
        runCurrent()

        assertEquals(
            listOf(
                PlayerPlaybackState(revision = 0, activeTrack = false, loadFailed = false),
                PlayerPlaybackState(revision = 1, activeTrack = true, loadFailed = false),
            ),
            received,
        )
        job.cancel()
    }

    @Test
    fun `Given the adapter presentation When the state is read repeatedly Then a fresh value is never cached`() {
        presentation.value = JdaPlayerPresentation(revision = 1)
        assertEquals(1L, port.playbackState.value.revision)

        presentation.value = JdaPlayerPresentation(revision = 7, activeTrack = true)
        assertEquals(PlayerPlaybackState(revision = 7, activeTrack = true), port.playbackState.value)
        assertEquals(listOf(port.playbackState.value), port.playbackState.replayCache)
    }

    @Test
    fun `Given a resolved target When connect runs Then resolve the channel by id and delegate once`() = runTest {
        every { guild.getVoiceChannelById(VOICE_CHANNEL_ID) } returns voiceChannel
        coEvery { adapter.connect(voiceChannel) } returns true

        val connected = port.connect(target())

        assertTrue(connected)
        verify(exactly = 1) { guild.getVoiceChannelById(VOICE_CHANNEL_ID) }
        coVerify(exactly = 1) { adapter.connect(voiceChannel) }
    }

    @Test
    fun `Given a refused session When connect runs Then report false without retrying`() = runTest {
        every { guild.getVoiceChannelById(VOICE_CHANNEL_ID) } returns voiceChannel
        coEvery { adapter.connect(voiceChannel) } returns false

        assertFalse(port.connect(target()))
        coVerify(exactly = 1) { adapter.connect(voiceChannel) }
    }

    @Test
    fun `Given a channel the bot can no longer see When connect runs Then fail without touching the adapter`() = runTest {
        every { guild.getVoiceChannelById(VOICE_CHANNEL_ID) } returns null

        assertFailsWith<IllegalStateException> { port.connect(target()) }
        coVerify(exactly = 0) { adapter.connect(any()) }
    }

    @Test
    fun `Given a channel id outside the signed range When connect runs Then fail without a gateway lookup`() = runTest {
        assertFailsWith<IllegalStateException> {
            port.connect(PlayerVoiceTarget(channelId = ChannelId(ULong.MAX_VALUE), channelName = "General"))
        }
        verify(exactly = 0) { guild.getVoiceChannelById(any<Long>()) }
        coVerify(exactly = 0) { adapter.connect(any()) }
    }

    private fun target(): PlayerVoiceTarget = PlayerVoiceTarget(
        channelId = ChannelId(VOICE_CHANNEL_ID.toULong()),
        channelName = "General",
    )

    private fun track(): PlayerTrack = PlayerTrack(
        identifier = "id",
        title = "Song",
        author = "Artist",
    )

    private companion object {
        private const val VOICE_CHANNEL_ID = 99L
    }
}
