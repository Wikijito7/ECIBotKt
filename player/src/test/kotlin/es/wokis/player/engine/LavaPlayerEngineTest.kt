package es.wokis.player.engine

import com.sedmelluq.discord.lavaplayer.filter.PcmFilterFactory
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventListener
import com.sedmelluq.discord.lavaplayer.player.event.TrackEndEvent
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo
import com.sedmelluq.discord.lavaplayer.track.playback.AudioFrame
import com.sedmelluq.discord.lavaplayer.track.playback.MutableAudioFrame
import es.wokis.player.engine.audio.PlayerAudioState
import es.wokis.player.engine.event.PlayerEvent
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Test
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LavaPlayerEngineTest {

    @Test
    fun `Given a loaded track When loadAndPlay completes Then it starts and becomes current`() = runBlocking {
        val harness = EngineHarness()
        harness.track("one")
        try {
            // When
            harness.engine.loadAndPlay("one")

            // Then
            awaitUntil { harness.engine.snapshot().current?.identifier == "one" }
            assertEquals(1, harness.player.startedTracks.count { it.identifier == "one" })
            assertEquals("one", harness.player.currentTrack?.identifier)
            assertTrue(harness.engine.isQueueEmpty())
        } finally {
            harness.close()
        }
    }

    @Test
    fun `Given several inputs When loadAndPlayMultiple runs Then queue order matches caller order`() = runBlocking {
        val harness = EngineHarness()
        listOf("one", "two", "three").forEach { harness.track(it) }
        try {
            // When
            harness.engine.loadAndPlayMultiple(listOf("one", "two", "three"))

            // Then
            awaitUntil {
                val snapshot = harness.engine.snapshot()
                snapshot.current?.identifier == "one" && snapshot.queue.map { it.identifier } == listOf("two", "three")
            }
            assertEquals(listOf("two", "three"), harness.engine.snapshot().queue.map { it.identifier })
        } finally {
            harness.close()
        }
    }

    @Test
    fun `Given an existing queue When a track is loaded with addToFront Then it becomes the next track`() = runBlocking {
        val harness = EngineHarness()
        listOf("one", "two", "three").forEach { harness.track(it) }
        try {
            // Given
            harness.engine.loadAndPlayMultiple(listOf("one", "two"))
            awaitUntil { harness.engine.snapshot().queue.size == 1 }

            // When
            harness.engine.loadAndPlay("three", addToFront = true)

            // Then
            awaitUntil { harness.engine.snapshot().queue.map { it.identifier } == listOf("three", "two") }
        } finally {
            harness.close()
        }
    }

    @Test
    fun `Given a queue When moveTrackToNext matches Then the track moves to the front`() = runBlocking {
        val harness = EngineHarness()
        listOf("one", "two", "three").forEach { harness.track(it) }
        try {
            // Given
            harness.engine.loadAndPlayMultiple(listOf("one", "two", "three"))
            awaitUntil { harness.engine.snapshot().queue.size == 2 }

            // When
            val moved = harness.engine.moveTrackToNext("three")

            // Then
            assertEquals("three", moved?.identifier)
            assertEquals(listOf("three", "two"), harness.engine.snapshot().queue.map { it.identifier })
        } finally {
            harness.close()
        }
    }

    @Test
    fun `Given a queue When shuffle runs Then every pending track is retained and current is unchanged`() = runBlocking {
        val harness = EngineHarness()
        listOf("one", "two", "three", "four").forEach { harness.track(it) }
        try {
            // Given
            harness.engine.loadAndPlayMultiple(listOf("one", "two", "three", "four"))
            awaitUntil { harness.engine.snapshot().queue.size == 3 }
            val before = harness.engine.snapshot()

            // When
            assertTrue(harness.engine.shuffle())

            // Then
            val after = harness.engine.snapshot()
            assertEquals(before.current?.identifier, after.current?.identifier)
            assertEquals(before.queue.map { it.identifier }.toSet(), after.queue.map { it.identifier }.toSet())
            assertEquals(before.queue.size, after.queue.size)
        } finally {
            harness.close()
        }
    }

    @Test
    fun `Given a playing track When stop runs Then state clears and the terminal event is emitted`() = runBlocking {
        val harness = EngineHarness()
        harness.track("one")
        try {
            // Given
            harness.engine.loadAndPlay("one")
            awaitUntil { harness.engine.snapshot().current != null }

            // When
            assertTrue(harness.engine.stop())

            // Then
            assertNull(harness.engine.snapshot().current)
            assertTrue(harness.engine.snapshot().queue.isEmpty())
            assertNull(harness.player.currentTrack)
        } finally {
            harness.close()
        }
    }

    @Test
    fun `Given a paused playing track When pause and resume complete Then the same track is kept`() = runBlocking {
        val harness = EngineHarness()
        harness.track("one")
        try {
            // Given
            harness.engine.loadAndPlay("one")
            awaitUntil { harness.engine.snapshot().current != null }

            // When
            assertTrue(harness.engine.pause())

            // Then
            assertTrue(harness.engine.isPaused())
            assertTrue(harness.player.pausedState)
            assertEquals("one", harness.engine.snapshot().current?.identifier)

            // When
            assertTrue(harness.engine.resume())

            // Then
            assertFalse(harness.engine.isPaused())
            assertFalse(harness.player.pausedState)
            assertEquals("one", harness.engine.snapshot().current?.identifier)
        } finally {
            harness.close()
        }
    }

    @Test
    fun `Given a paused queue When skip runs Then the next track is current and still paused`() = runBlocking {
        val harness = EngineHarness()
        listOf("one", "two").forEach { harness.track(it) }
        try {
            // Given
            harness.engine.loadAndPlayMultiple(listOf("one", "two"))
            awaitUntil { harness.engine.snapshot().queue.size == 1 }
            assertTrue(harness.engine.pause())

            // When
            assertTrue(harness.engine.skip())

            // Then
            awaitUntil { harness.engine.snapshot().current?.identifier == "two" }
            assertTrue(harness.engine.isPaused())
            assertTrue(harness.player.pausedState)
            assertEquals("two", harness.engine.snapshot().current?.identifier)

            // When
            assertTrue(harness.engine.resume())

            // Then
            assertFalse(harness.engine.isPaused())
            assertEquals("two", harness.engine.snapshot().current?.identifier)
        } finally {
            harness.close()
        }
    }

    @Test
    fun `Given concurrent load submissions When the mailbox drains Then no track is lost or duplicated`() = runBlocking {
        val harness = EngineHarness()
        val inputs = (0 until 20).map { "track-$it" }
        inputs.forEach { harness.track(it) }
        try {
            // When
            coroutineScope {
                inputs.map { input -> async { harness.engine.loadAndPlay(input) } }.awaitAll()
            }

            // Then
            awaitUntil {
                val snapshot = harness.engine.snapshot()
                (listOfNotNull(snapshot.current) + snapshot.queue).size == inputs.size
            }
            val snapshot = harness.engine.snapshot()
            val identifiers = (listOfNotNull(snapshot.current) + snapshot.queue).map { it.identifier }
            assertEquals(inputs.size, identifiers.size)
            assertEquals(inputs.toSet(), identifiers.toSet())
        } finally {
            harness.close()
        }
    }

    @Test
    fun `Given a Lavaplayer callback When it runs Then it only enqueues and returns without mutating state inline`() = runBlocking {
        val harness = EngineHarness()
        val track = harness.track("one")
        try {
            // Given
            harness.engine.loadAndPlay("one")
            awaitUntil { harness.engine.snapshot().current?.identifier == "one" }
            val listener = harness.player.listeners.single()

            // When
            val callbackReturned = CountDownLatch(1)
            val callbackThread = Thread({
                listener.onEvent(TrackEndEvent(harness.player, track, AudioTrackEndReason.FINISHED))
                callbackReturned.countDown()
            }, "lavaplayer-callback")
            callbackThread.start()

            // Then: the bounded latch proves the callback returned without touching state inline.
            assertTrue(callbackReturned.await(1, TimeUnit.SECONDS), "the callback did not return promptly")
            callbackThread.join(1_000)
            assertFalse(callbackThread.isAlive)
            // Ordered command barrier: once Pause is acknowledged, the prior TrackEnded command
            // has been handled by the same actor.
            assertTrue(harness.engine.pause())
            assertNull(harness.engine.snapshot().current)
        } finally {
            harness.close()
        }
    }

    @Test
    fun `Given a retryable end When retry fires Then the track is cloned and restarted`() = runBlocking {
        val harness = EngineHarness()
        val track = harness.track("one")
        try {
            // Given
            harness.engine.loadAndPlay("one")
            awaitUntil { harness.engine.snapshot().current?.identifier == "one" }
            val listener = harness.player.listeners.single()

            // When
            listener.onEvent(TrackEndEvent(harness.player, track, AudioTrackEndReason.LOAD_FAILED))

            // Then
            awaitUntil(3_000) { harness.player.startedTracks.size >= 2 }
            assertEquals("one", harness.player.currentTrack?.identifier)
        } finally {
            harness.close()
        }
    }

    @Test
    fun `Given an owned engine When close runs Then player and manager are shut down once`() {
        val harness = EngineHarness()
        harness.track("one")

        harness.engine.close()
        harness.engine.close()

        assertTrue(harness.player.destroyed)
        verify(exactly = 1) { harness.manager.shutdown() }
    }

    @Test
    fun `Given blocking sync load When pause skip and stop run Then the actor stays responsive and no stale track starts`() = runBlocking {
        val harness = EngineHarness()
        val ttsTrack = harness.track("tts")
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        every { harness.manager.loadItemSync("tts") } answers {
            started.countDown()
            release.await(5, TimeUnit.SECONDS)
            ttsTrack
        }
        val load = async(Dispatchers.Default) { harness.engine.loadAndPlayTts("tts") }
        try {
            assertTrue(started.await(2, TimeUnit.SECONDS), "the blocking loader never started")

            // When
            val paused = withTimeout(1_000) { harness.engine.pause() }
            val skipped = withTimeout(1_000) { harness.engine.skip() }
            val stopped = withTimeout(1_000) { harness.engine.stop() }

            // Then
            assertTrue(paused)
            assertFalse(skipped)
            assertTrue(stopped)
            release.countDown()
            withTimeout(2_000) { load.await() }
            assertTrue(harness.player.startedTracks.none { it.identifier == "tts" })
            assertNull(harness.engine.snapshot().current)
        } finally {
            release.countDown()
            harness.close()
        }
    }

    @Test
    fun `Given a blocking sync load When close runs Then returning late cannot start the track`() = runBlocking {
        val harness = EngineHarness()
        val ttsTrack = harness.track("tts")
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        every { harness.manager.loadItemSync("tts") } answers {
            started.countDown()
            release.await(5, TimeUnit.SECONDS)
            ttsTrack
        }
        val load = async(Dispatchers.Default) { harness.engine.loadAndPlayTts("tts") }
        try {
            assertTrue(started.await(2, TimeUnit.SECONDS), "the blocking loader never started")

            // When
            harness.engine.close()
            release.countDown()
            withTimeout(2_000) { load.await() }

            // Then
            assertTrue(harness.player.startedTracks.none { it.identifier == "tts" })
            assertNull(harness.engine.snapshot().current)
        } finally {
            release.countDown()
            harness.close()
        }
    }

    @Test
    fun `Given a blocking sync load When the caller cancels Then returning late cannot start the track`() = runBlocking {
        val harness = EngineHarness()
        val ttsTrack = harness.track("tts")
        val started = CountDownLatch(1)
        val finished = CountDownLatch(1)
        val release = CountDownLatch(1)
        every { harness.manager.loadItemSync("tts") } answers {
            started.countDown()
            release.await(5, TimeUnit.SECONDS)
            finished.countDown()
            ttsTrack
        }
        val load = launch(Dispatchers.Default) { harness.engine.loadAndPlayTts("tts") }
        try {
            assertTrue(started.await(2, TimeUnit.SECONDS), "the blocking loader never started")

            // When
            load.cancelAndJoin()
            release.countDown()
            assertTrue(finished.await(2, TimeUnit.SECONDS))
            // A discarded load has no completion callback; allow the IO worker one scheduler turn.
            delay(100)

            // Then
            assertTrue(harness.player.startedTracks.none { it.identifier == "tts" })
            assertNull(harness.engine.snapshot().current)
        } finally {
            release.countDown()
            harness.close()
        }
    }

    @Test
    fun `Given delayed async loads When callbacks complete later Then caller order and one ordering key are preserved`() = runBlocking {
        val harness = EngineHarness()
        val inputs = listOf("one", "two", "three")
        val tracks = inputs.associateWith { harness.track(it) }
        val captured = CopyOnWriteArrayList<CapturedLoad>()
        val capturedAll = CountDownLatch(inputs.size)
        every { harness.manager.loadItemOrdered(any(), any<String>(), any()) } answers {
            captured += CapturedLoad(
                orderKey = firstArg(),
                input = secondArg(),
                handler = thirdArg<AudioLoadResultHandler>(),
            )
            capturedAll.countDown()
            CompletableFuture.completedFuture(null)
        }
        try {
            // When
            harness.engine.loadAndPlayMultiple(inputs)
            assertTrue(capturedAll.await(2, TimeUnit.SECONDS), "the batch loads were not submitted")

            // Then: every request in the call shares the same serialized ordering key.
            assertEquals(1, captured.map { it.orderKey }.distinct().size)
            assertEquals(inputs, captured.map { it.input })

            // Complete the delayed providers after the batch has already been submitted.
            captured.forEach { load -> load.handler.trackLoaded(tracks.getValue(load.input)) }

            awaitUntil {
                val snapshot = harness.engine.snapshot()
                snapshot.current?.identifier == "one" &&
                    snapshot.queue.map { it.identifier } == listOf("two", "three")
            }
        } finally {
            harness.close()
        }
    }

    @Test
    fun `Given several inputs with a custom name When the batch runs Then every track keeps the override`() = runBlocking {
        val harness = EngineHarness()
        listOf("one", "two").forEach { harness.track(it) }
        try {
            // When
            harness.engine.loadAndPlayMultipleWithCustomName(listOf("one", "two"), "Custom")

            // Then
            awaitUntil {
                val snapshot = harness.engine.snapshot()
                snapshot.current?.customName == "Custom" &&
                    snapshot.queue.singleOrNull()?.customName == "Custom"
            }
            assertEquals("one", harness.engine.snapshot().current?.identifier)
            assertEquals("two", harness.engine.snapshot().queue.single().identifier)
        } finally {
            harness.close()
        }
    }

    @Test
    fun `Given a radio input When playRadio runs Then the custom presentation fields are kept`() = runBlocking {
        val harness = EngineHarness()
        harness.track("radio")
        try {
            // When
            harness.engine.playRadio("Radio Name", "radio", "https://example.test/icon.png")

            // Then
            awaitUntil { harness.engine.snapshot().current?.identifier == "radio" }
            val current = harness.engine.snapshot().current
            assertEquals("Radio Name", current?.customName)
            assertEquals("https://example.test/icon.png", current?.customFavicon)
        } finally {
            harness.close()
        }
    }

    @Test
    fun `Given an async playlist result When load completes Then all tracks are queued in provider order`() = runBlocking {
        val harness = EngineHarness()
        val first = harness.track("first")
        val second = harness.track("second")
        val playlist = mockk<AudioPlaylist> {
            every { name } returns "Mix"
            every { tracks } returns listOf(first, second)
        }
        every { harness.manager.loadItemOrdered(any(), "playlist", any()) } answers {
            thirdArg<AudioLoadResultHandler>().playlistLoaded(playlist)
            CompletableFuture.completedFuture(null)
        }
        try {
            // When
            harness.engine.loadAndPlay("playlist")

            // Then
            awaitUntil {
                val snapshot = harness.engine.snapshot()
                snapshot.current?.identifier == "first" &&
                    snapshot.queue.map { it.identifier } == listOf("second")
            }
        } finally {
            harness.close()
        }
    }

    @Test
    fun `Given no match and load failure callbacks When processed Then both failure events are observable`() = runBlocking {
        val harness = EngineHarness()
        val events = mutableListOf<PlayerEvent>()
        val subscribed = CompletableDeferred<Unit>()
        val collector = launch(Dispatchers.Default) {
            harness.engine.events.onStart { subscribed.complete(Unit) }.collect { events += it }
        }
        every { harness.manager.loadItemOrdered(any(), "missing", any()) } answers {
            thirdArg<AudioLoadResultHandler>().noMatches()
            CompletableFuture.completedFuture(null)
        }
        every { harness.manager.loadItemOrdered(any(), "failed", any()) } answers {
            thirdArg<AudioLoadResultHandler>().loadFailed(
                FriendlyException("load boom", FriendlyException.Severity.COMMON, null),
            )
            CompletableFuture.completedFuture(null)
        }
        try {
            subscribed.await()

            // When
            harness.engine.loadAndPlay("missing")
            awaitUntil { events.any { it is PlayerEvent.NoMatches } }
            harness.engine.loadAndPlay("failed")
            awaitUntil { events.any { it is PlayerEvent.LoadFailed } }

            // Then
            assertEquals("missing", (events.filterIsInstance<PlayerEvent.NoMatches>().single()).input)
            assertEquals("load boom", events.filterIsInstance<PlayerEvent.LoadFailed>().single().message)
            assertNull(harness.engine.snapshot().current)
        } finally {
            collector.cancelAndJoin()
            harness.close()
        }
    }

    @Test
    fun `Given an async load in flight When stop runs before completion Then the late result is discarded`() = runBlocking {
        val harness = EngineHarness()
        val track = harness.track("late")
        val captured = CompletableDeferred<AudioLoadResultHandler>()
        every { harness.manager.loadItemOrdered(any(), "late", any()) } answers {
            captured.complete(thirdArg())
            CompletableFuture.completedFuture(null)
        }
        try {
            harness.engine.loadAndPlay("late")
            val handler = withTimeout(2_000) { captured.await() }

            // When
            assertTrue(harness.engine.stop())
            handler.trackLoaded(track)
            // The discarded callback is processed by the mailbox; wait one bounded scheduler turn.
            delay(100)

            // Then
            assertNull(harness.engine.snapshot().current)
            assertTrue(harness.engine.snapshot().queue.isEmpty())
            assertTrue(harness.player.startedTracks.none { it.identifier == "late" })
        } finally {
            harness.close()
        }
    }

    @Test
    fun `Given a paused track When pause is acknowledged Then position is unchanged and resume keeps the same track`() = runBlocking {
        val harness = EngineHarness()
        val position = AtomicLong(0L)
        harness.track("one", positionProvider = { position.get() })
        try {
            harness.engine.loadAndPlay("one")
            awaitUntil { harness.engine.snapshot().current?.identifier == "one" }
            position.set(1_234L)

            // When
            assertTrue(harness.engine.pause())

            // Then
            assertEquals(1_234L, harness.engine.snapshot().current?.positionMillis)
            assertEquals(1_234L, harness.engine.snapshot().current?.positionMillis)
            assertTrue(harness.engine.isPaused())
            val startedBeforeResume = harness.player.startedTracks.size

            // When
            assertTrue(harness.engine.resume())

            // Then
            assertEquals("one", harness.engine.snapshot().current?.identifier)
            assertEquals(1_234L, harness.engine.snapshot().current?.positionMillis)
            assertEquals(startedBeforeResume, harness.player.startedTracks.size)
            assertFalse(harness.engine.isPaused())
        } finally {
            harness.close()
        }
    }

    @Test
    fun `Given a provider failure When the audio stream fails Then PlaybackFailed is emitted and the queue advances`() = runBlocking {
        val harness = EngineHarness()
        harness.track("one")
        harness.track("two")
        val events = mutableListOf<PlayerEvent>()
        val subscribed = CompletableDeferred<Unit>()
        val collector = launch(Dispatchers.Default) {
            harness.engine.events.onStart { subscribed.complete(Unit) }.collect { events += it }
        }
        try {
            subscribed.await()
            harness.engine.loadAndPlayMultiple(listOf("one", "two"))
            awaitUntil { harness.engine.snapshot().queue.size == 1 }
            harness.player.frameProvider = { throw IllegalStateException("provider boom") }

            // When
            val stream = harness.engine.audioStream()
            val first = stream.next()
            val second = stream.next()

            // Then
            assertEquals(PlayerAudioState.Ended, first)
            assertEquals(PlayerAudioState.Ended, second)
            awaitUntil { events.any { it is PlayerEvent.PlaybackFailed } }
            val failure = events.filterIsInstance<PlayerEvent.PlaybackFailed>().single()
            assertEquals("provider boom", failure.message)
            awaitUntil { harness.engine.snapshot().current?.identifier == "two" }
        } finally {
            collector.cancelAndJoin()
            harness.close()
        }
    }

    @Test
    fun `Given a single subscriber When a second collector starts Then it fails explicitly`() = runBlocking {
        val harness = EngineHarness()
        val subscribed = CompletableDeferred<Unit>()
        val firstCollector = launch(Dispatchers.Default) {
            harness.engine.events.onStart { subscribed.complete(Unit) }.collect {}
        }
        try {
            subscribed.await()

            // When
            val failure = assertFailsWith<IllegalStateException> {
                withTimeout(1_000) { harness.engine.events.first() }
            }

            // Then
            assertTrue(failure.message.orEmpty().contains("exactly one subscriber"))
        } finally {
            firstCollector.cancelAndJoin()
            harness.close()
        }
    }

    private data class CapturedLoad(
        val orderKey: Any,
        val input: String,
        val handler: AudioLoadResultHandler,
    )

    private suspend fun awaitUntil(timeoutMillis: Long = 2_000, condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
        while (!condition()) {
            if (System.nanoTime() > deadline) {
                error("Condition was not met within ${timeoutMillis}ms")
            }
            delay(5)
        }
    }

    private class EngineHarness : AutoCloseable {
        val player = FakeAudioPlayer()
        val manager: AudioPlayerManager = mockk()
        private val tracks = mutableMapOf<String, AudioTrack>()
        val engine: LavaPlayerEngine

        init {
            every { manager.createPlayer() } returns player
            every { manager.shutdown() } just Runs
            engine = LavaPlayerEngine(manager, ownsManager = true)
            every { manager.loadItemOrdered(any(), any<String>(), any()) } answers {
                val input = secondArg<String>()
                val handler = thirdArg<com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler>()
                handler.trackLoaded(tracks.getValue(input))
                CompletableFuture.completedFuture(null)
            }
            every { manager.loadItemSync(any<String>()) } answers {
                tracks.getValue(firstArg())
            }
        }

        fun track(
            identifier: String,
            title: String = identifier,
            positionProvider: () -> Long = { 0L },
        ): AudioTrack {
            val info =
                AudioTrackInfo(title, "author", 1_000L, identifier, false, "https://example/$identifier", null, null)
            val sourceManager = mockk<AudioSourceManager> {
                every { sourceName } returns "test"
            }
            val clone = mockk<AudioTrack>()
            every { clone.info } returns info
            every { clone.identifier } returns identifier
            every { clone.position } returns 0L
            every { clone.sourceManager } returns sourceManager
            val track = mockk<AudioTrack>()
            every { track.info } returns info
            every { track.identifier } returns identifier
            every { track.position } answers { positionProvider() }
            every { track.duration } returns 1_000L
            every { track.sourceManager } returns sourceManager
            every { track.makeClone() } returns clone
            tracks[identifier] = track
            return track
        }

        override fun close() {
            engine.close()
        }
    }
}

private class FakeAudioPlayer : AudioPlayer {
    var currentTrack: AudioTrack? = null
    var pausedState: Boolean = false
    var destroyed: Boolean = false
    val startedTracks = mutableListOf<AudioTrack>()
    val listeners = mutableListOf<AudioEventListener>()
    var frameProvider: () -> AudioFrame? = { null }
    var provideCalls: Int = 0

    override fun getPlayingTrack(): AudioTrack? = currentTrack

    override fun playTrack(track: AudioTrack?) {
        currentTrack = track
    }

    override fun startTrack(track: AudioTrack?, noInterrupt: Boolean): Boolean {
        track ?: return false
        startedTracks += track
        currentTrack = track
        return true
    }

    override fun stopTrack() {
        currentTrack = null
    }

    override fun getVolume(): Int = 100

    override fun setVolume(volume: Int) = Unit

    override fun setFilterFactory(factory: PcmFilterFactory?) = Unit

    override fun setFrameBufferDuration(duration: Int?) = Unit

    override fun isPaused(): Boolean = pausedState

    override fun setPaused(value: Boolean) {
        pausedState = value
    }

    override fun destroy() {
        destroyed = true
        currentTrack = null
    }

    override fun addListener(listener: AudioEventListener) {
        listeners += listener
    }

    override fun removeListener(listener: AudioEventListener) {
        listeners -= listener
    }

    override fun checkCleanup(threshold: Long) = Unit

    override fun provide(): AudioFrame? {
        provideCalls++
        return frameProvider()
    }

    override fun provide(timeout: Long, unit: TimeUnit): AudioFrame? {
        provideCalls++
        return frameProvider()
    }

    override fun provide(targetFrame: MutableAudioFrame): Boolean = false

    override fun provide(targetFrame: MutableAudioFrame, timeout: Long, unit: TimeUnit): Boolean = false
}
