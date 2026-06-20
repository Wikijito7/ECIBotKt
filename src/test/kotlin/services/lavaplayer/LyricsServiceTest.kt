package services.lavaplayer

import com.github.topi314.lavalyrics.AudioLyricsManager
import com.github.topi314.lavalyrics.lyrics.AudioLyrics
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import es.wokis.services.lavaplayer.LyricsService
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LyricsServiceTest {

    private fun createTrackMock(): AudioTrack {
        val sourceManager = mockk<AudioSourceManager> {
            every { sourceName } returns "unknown"
        }
        return mockk<AudioTrack> {
            every { getSourceManager() } returns sourceManager
        }
    }

    @Test
    fun `When lyrics are found Then getFormattedLyrics returns text`() {
        // Given
        val lyricsText = "Line 1\nLine 2\nLine 3"
        val audioLyrics = mockk<AudioLyrics> {
            every { text } returns lyricsText
        }
        val lyricsManager = mockk<AudioLyricsManager> {
            every { sourceName } returns "test"
            every { loadLyrics(any()) } returns audioLyrics
        }
        val lyricsService = LyricsService()
        lyricsService.registerLyricsManager(lyricsManager)
        val track = createTrackMock()

        // When
        val result = lyricsService.getFormattedLyrics(track)

        // Then
        assertEquals(lyricsText, result)
    }

    @Test
    fun `When no lyrics available Then getFormattedLyrics returns null`() {
        // Given
        val lyricsManager = mockk<AudioLyricsManager> {
            every { sourceName } returns "test"
            every { loadLyrics(any()) } returns null
        }
        val lyricsService = LyricsService()
        lyricsService.registerLyricsManager(lyricsManager)
        val track = createTrackMock()

        // When
        val result = lyricsService.getFormattedLyrics(track)

        // Then
        assertNull(result)
    }

    @Test
    fun `When first manager returns null Then tries second manager`() {
        // Given
        val lyricsText = "Found by second manager"
        val firstManager = mockk<AudioLyricsManager> {
            every { sourceName } returns "first"
            every { loadLyrics(any()) } returns null
        }
        val audioLyrics = mockk<AudioLyrics> {
            every { text } returns lyricsText
        }
        val secondManager = mockk<AudioLyricsManager> {
            every { sourceName } returns "second"
            every { loadLyrics(any()) } returns audioLyrics
        }
        val lyricsService = LyricsService()
        lyricsService.registerLyricsManager(firstManager)
        lyricsService.registerLyricsManager(secondManager)
        val track = createTrackMock()

        // When
        val result = lyricsService.getFormattedLyrics(track)

        // Then
        assertEquals(lyricsText, result)
    }

    @Test
    fun `When no managers registered Then loadLyrics returns null`() {
        // Given
        val lyricsService = LyricsService()
        val track = createTrackMock()

        // When
        val result = lyricsService.loadLyrics(track)

        // Then
        assertNull(result)
    }

    @Test
    fun `When all managers return null Then getFormattedLyrics returns null`() {
        // Given
        val firstManager = mockk<AudioLyricsManager> {
            every { sourceName } returns "first"
            every { loadLyrics(any()) } returns null
        }
        val secondManager = mockk<AudioLyricsManager> {
            every { sourceName } returns "second"
            every { loadLyrics(any()) } returns null
        }
        val lyricsService = LyricsService()
        lyricsService.registerLyricsManager(firstManager)
        lyricsService.registerLyricsManager(secondManager)
        val track = createTrackMock()

        // When
        val result = lyricsService.getFormattedLyrics(track)

        // Then
        assertNull(result)
    }
}
