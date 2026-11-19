package es.wokis.jda.player

import es.wokis.player.engine.event.PlayerEvent
import es.wokis.player.engine.model.PlayerTrack
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
internal class JdaGuildPlayerVoiceAdapterFanOutTest : PlayerVoiceAdapterTestSupport() {

    @Test
    fun `Given a burst larger than the fan-out buffer When collected Then every event arrives once and in order`() = runTest {
        val fixture = voiceFixture()
        val received = mutableListOf<PlayerEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            fixture.adapter.events.collect { received += it }
        }
        runCurrent()
        // Above EVENT_FAN_OUT_CAPACITY (256): the pump must suspend on overflow instead of dropping.
        val expected = (1..BURST_SIZE).map { index ->
            PlayerEvent.TrackQueued(track(index), addToFront = false, uri = null)
        }

        expected.forEach { event -> fixture.events.emit(event) }
        advanceUntilIdle()

        assertEquals<List<PlayerEvent>>(expected, received, "the fan-out must not drop, duplicate or reorder a burst")
        assertEquals(expected.size, received.distinct().size)
        verify(exactly = 1) { fixture.engine.events }
    }

    @Test
    fun `Given a subscribed fan-out When the adapter closes Then the flow completes`() = runTest {
        val fixture = voiceFixture()
        val completed = CompletableDeferred<Unit>()
        val received = mutableListOf<PlayerEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            fixture.adapter.events.collect { received += it }
            completed.complete(Unit)
        }
        runCurrent()
        fixture.events.emit(PlayerEvent.TrackQueued(track(1), addToFront = false, uri = null))
        advanceUntilIdle()

        fixture.adapter.close()
        advanceUntilIdle()

        assertEquals(1, received.size)
        assertTrue(completed.isCompleted, "the fan-out flow must complete when the adapter closes")
    }

    private fun track(index: Int): PlayerTrack = PlayerTrack(
        identifier = "track-$index",
        title = "Track $index",
        author = "Artist",
    )

    private companion object {
        private const val BURST_SIZE = 600
    }
}
