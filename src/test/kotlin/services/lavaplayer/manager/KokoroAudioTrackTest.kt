package services.lavaplayer.manager

import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo
import io.mockk.mockk
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class KokoroAudioTrackTest {

    @Test
    fun `When getSourceManager is called Then return KokoroSourceManager`() {
        // Given
        val trackInfo = AudioTrackInfo(
            "TTS Message: Hello",
            "Kokoro",
            Long.MAX_VALUE,
            "kokoro://?text=Hello",
            true,
            "kokoro://?text=Hello",
            null,
            null
        )
        val sourceManager = mockk<KokoroSourceManager>()
        val track = KokoroAudioTrack(trackInfo, sourceManager)

        // When
        val actual = track.sourceManager

        // Then
        assertEquals(sourceManager, actual)
    }

    @Test
    fun `When track is created Then have correct default properties`() {
        // Given
        val trackInfo = AudioTrackInfo(
            "TTS Message: Hello",
            "Kokoro",
            Long.MAX_VALUE,
            "kokoro://?text=Hello",
            true,
            "kokoro://?text=Hello",
            null,
            null
        )
        val sourceManager = mockk<KokoroSourceManager>()

        // When
        val track = KokoroAudioTrack(trackInfo, sourceManager)

        // Then
        assertEquals("", track.baseUrl)
        assertEquals("", track.rawText)
        assertEquals("", track.voice)
        assertEquals(1.0f, track.speed)
        assertEquals("", track.langCode)
    }

    @Test
    fun `When track properties are set Then properties are stored correctly`() {
        // Given
        val trackInfo = AudioTrackInfo(
            "TTS Message: Hello",
            "Kokoro",
            Long.MAX_VALUE,
            "kokoro://?text=Hello",
            true,
            "kokoro://?text=Hello",
            null,
            null
        )
        val sourceManager = mockk<KokoroSourceManager>()
        val track = KokoroAudioTrack(trackInfo, sourceManager)

        // When
        track.baseUrl = "http://localhost:8080"
        track.rawText = "Test message"
        track.voice = "em_santa"
        track.speed = 0.8f
        track.langCode = "e"

        // Then
        assertEquals("http://localhost:8080", track.baseUrl)
        assertEquals("Test message", track.rawText)
        assertEquals("em_santa", track.voice)
        assertEquals(0.8f, track.speed)
        assertEquals("e", track.langCode)
    }

    @Test
    fun `When track info contains special characters in text Then store them correctly`() {
        // Given
        val trackInfo = AudioTrackInfo(
            "TTS Message: Special",
            "Kokoro",
            Long.MAX_VALUE,
            "kokoro://?text=Special",
            true,
            "kokoro://?text=Special",
            null,
            null
        )
        val sourceManager = mockk<KokoroSourceManager>()
        val track = KokoroAudioTrack(trackInfo, sourceManager)

        // When
        track.rawText = "Hello \"quoted\" text with \\ backslash and new\nline"

        // Then
        assertEquals("Hello \"quoted\" text with \\ backslash and new\nline", track.rawText)
    }

    @Test
    fun `When track info is accessed Then return correct info`() {
        // Given
        val trackInfo = AudioTrackInfo(
            "TTS Message: Hello",
            "Kokoro",
            Long.MAX_VALUE,
            "kokoro://?text=Hello",
            true,
            "kokoro://?text=Hello",
            null,
            null
        )
        val sourceManager = mockk<KokoroSourceManager>()
        val track = KokoroAudioTrack(trackInfo, sourceManager)

        // When/Then
        assertEquals("TTS Message: Hello", track.info.title)
        assertEquals("Kokoro", track.info.author)
        assertEquals(Long.MAX_VALUE, track.info.length)
        assertEquals("kokoro://?text=Hello", track.info.identifier)
        assertTrue(track.info.isStream)
    }
}