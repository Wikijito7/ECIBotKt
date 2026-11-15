package es.wokis.player.engine.event

import es.wokis.player.engine.model.PlayerSnapshot
import es.wokis.player.engine.model.PlayerTrack
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlayerEventTest {

    private val track = PlayerTrack(
        identifier = "id",
        title = "title",
        author = "author",
    )

    @Test
    fun `Given queue events When inspected Then their payload fields are exposed`() {
        // Given
        val queued = PlayerEvent.TrackQueued(track, addToFront = true, uri = "https://example.test/track")
        val playlist = PlayerEvent.PlaylistQueued("Mix", 2, "https://example.test/list", addToFront = false)
        val changed = PlayerEvent.QueueChanged(PlayerSnapshot(current = track, queue = listOf(track), paused = true))

        // Then
        assertEquals(track, queued.track)
        assertTrue(queued.addToFront)
        assertEquals("https://example.test/track", queued.uri)
        assertEquals("Mix", playlist.name)
        assertEquals(2, playlist.trackCount)
        assertEquals("https://example.test/list", playlist.playlistUrl)
        assertEquals(track, changed.snapshot.current)
        assertTrue(changed.snapshot.paused)
    }

    @Test
    fun `Given lifecycle and failure events When inspected Then their payload fields are exposed`() {
        // Given
        val noMatches = PlayerEvent.NoMatches("missing")
        val loadFailed = PlayerEvent.LoadFailed("boom")
        val started = PlayerEvent.TrackStarted(track)
        val ended = PlayerEvent.TrackEnded(queueEmpty = true)
        val playbackFailed = PlayerEvent.PlaybackFailed("provider boom")

        // Then
        assertEquals("missing", noMatches.input)
        assertEquals("boom", loadFailed.message)
        assertEquals(track, started.track)
        assertTrue(ended.queueEmpty)
        assertEquals("provider boom", playbackFailed.message)
    }
}
