package es.wokis.player.engine.model

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlayerModelTest {

    @Test
    fun `Given custom name title and generated display Then display name precedence is stable`() {
        // Given
        val custom = track(customName = "Custom")
        val titled = track(title = "Artist - Song")
        val generated = track()

        // Then
        assertEquals("Custom", custom.displayName)
        assertEquals("Artist - Song", titled.displayName)
        assertEquals("author - title", generated.displayName)
    }

    @Test
    fun `Given a player track When constructed Then all neutral fields are exposed`() {
        // Given
        val track = track(
            identifier = "track-id",
            title = "Track",
            uri = "https://example.test/track",
            isStream = true,
            positionMillis = 123L,
        )

        // Then
        assertEquals("track-id", track.identifier)
        assertEquals("Track", track.title)
        assertEquals("author", track.author)
        assertEquals("https://example.test/track", track.uri)
        assertTrue(track.isStream)
        assertEquals(123L, track.positionMillis)
        assertEquals(Long.MAX_VALUE, PlayerTrack.UNKNOWN_DURATION)
    }

    @Test
    fun `Given player snapshots When constructed Then current queue and pause state are exposed`() {
        // Given
        val current = track()
        val queued = track(identifier = "queued")

        // When
        val snapshot = PlayerSnapshot(current = current, queue = listOf(queued), paused = true, hasActiveTrack = true)

        // Then
        assertEquals(current, snapshot.current)
        assertEquals(listOf(queued), snapshot.queue)
        assertTrue(snapshot.paused)
        assertTrue(snapshot.hasActiveTrack)
        assertFalse(PlayerSnapshot().paused)
        assertTrue(PlayerSnapshot().queue.isEmpty())
    }

    private fun track(
        identifier: String = "id",
        title: String = "title",
        customName: String? = null,
        uri: String? = null,
        isStream: Boolean = false,
        positionMillis: Long = 0L,
    ): PlayerTrack = PlayerTrack(
        identifier = identifier,
        title = title,
        author = "author",
        uri = uri,
        isStream = isStream,
        customName = customName,
        positionMillis = positionMillis,
    )
}
