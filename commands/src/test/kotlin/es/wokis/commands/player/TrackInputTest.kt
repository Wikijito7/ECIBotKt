package es.wokis.commands.player

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TrackInputTest {

    @Test
    fun `Given http or https input When classified Then it is a track url`() {
        assertTrue("http://example.invalid/track".isTrackUrl())
        assertTrue("https://example.invalid/track".isTrackUrl())
    }

    @Test
    fun `Given a scheme other than http or https When classified Then it is a search term`() {
        assertFalse("ftp://example.invalid/track".isTrackUrl())
        assertFalse("www.example.invalid/track".isTrackUrl())
        assertFalse("example.invalid/track".isTrackUrl())
        assertFalse("HTTPS://example.invalid/track".isTrackUrl(), "Kord matches the scheme case-sensitively")
        assertFalse("song name".isTrackUrl())
    }

    @Test
    fun `Given a monochrome https link When transformed Then keep the scheme and reuse the path`() {
        assertEquals(
            "https://tidal.com/album/123",
            "https://monochrome.example/album/123".transformMonochromeToTidal(),
        )
    }

    @Test
    fun `Given a monochrome http link When transformed Then keep the original scheme`() {
        assertEquals(
            "http://tidal.com/track/1",
            "http://monochrome.example/track/1".transformMonochromeToTidal(),
        )
    }

    @Test
    fun `Given a monochrome link without a scheme When transformed Then default to https`() {
        assertEquals("https://tidal.com/album/9", "monochrome.example/album/9".transformMonochromeToTidal())
    }

    @Test
    fun `Given a monochrome host without a path When transformed Then emit the bare Tidal domain`() {
        assertEquals("https://tidal.com", "https://monochrome.example".transformMonochromeToTidal())
    }

    @Test
    fun `Given a monochrome link with a query When transformed Then preserve the path and query`() {
        assertEquals(
            "https://tidal.com/track/1?si=abc",
            "https://monochrome.example/track/1?si=abc".transformMonochromeToTidal(),
        )
    }

    @Test
    fun `Given a mixed case monochrome link When transformed Then match the keyword case insensitively`() {
        assertEquals("https://tidal.com/track/2", "https://MONOCHROME.example/track/2".transformMonochromeToTidal())
    }

    @Test
    fun `Given a non monochrome link When transformed Then return it untouched`() {
        assertEquals(
            "https://example.invalid/track",
            "https://example.invalid/track".transformMonochromeToTidal(),
        )
        assertEquals("song name", "song name".transformMonochromeToTidal())
    }
}
