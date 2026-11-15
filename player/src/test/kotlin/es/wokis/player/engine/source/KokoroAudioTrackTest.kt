package es.wokis.player.engine.source

import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.apache.http.client.methods.HttpUriRequest
import org.junit.jupiter.api.Test
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class KokoroAudioTrackTest {

    @Test
    fun `Given a KokoroAudioTrack When getSourceManager is called Then return KokoroSourceManager`() {
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
    fun `Given a KokoroAudioTrack When track is created Then have correct default properties`() {
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
    fun `Given a KokoroAudioTrack When track properties are set Then properties are stored correctly`() {
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
    fun `Given a KokoroAudioTrack When track info contains special characters in text Then store them correctly`() {
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
    fun `Given a KokoroAudioTrack When track info is accessed Then return correct info`() {
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

    @Test
    fun `Given a KokoroAudioTrack When track is shallow cloned Then every Kokoro field and the source manager are preserved`() {
        // Given
        val trackInfo =
            AudioTrackInfo(
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
        val track = KokoroAudioTrack(trackInfo, sourceManager).apply {
            baseUrl = "http://localhost:8880"
            rawText = "Hello"
            voice = "af_sarah"
            speed = 0.75f
            langCode = "es"
        }

        // When
        val clone = track.makeClone() as KokoroAudioTrack

        // Then
        assertTrue(clone !== track)
        assertEquals(trackInfo, clone.info)
        assertEquals(track.baseUrl, clone.baseUrl)
        assertEquals(track.rawText, clone.rawText)
        assertEquals(track.voice, clone.voice)
        assertEquals(track.speed, clone.speed)
        assertEquals(track.langCode, clone.langCode)
        assertEquals(sourceManager, clone.sourceManager)
    }

    @Test
    fun `Given a KokoroAudioTrack When process executes Then the POST body is built and the IO failure propagates`() {
        // Given
        val trackInfo =
            AudioTrackInfo(
                "TTS Message: Hello",
                "Kokoro",
                Long.MAX_VALUE,
                "kokoro://?text=Hello",
                true,
                "kokoro://?text=Hello",
                null,
                null
            )
        val request = slot<HttpUriRequest>()
        val http = mockk<HttpInterface> {
            every { execute(capture(request)) } throws IOException("boom")
            justRun { close() }
        }
        val sourceManager = mockk<KokoroSourceManager> {
            every { getHttpInterface() } returns http
        }
        val track = KokoroAudioTrack(trackInfo, sourceManager).apply {
            baseUrl = "http://localhost:8880"
            rawText = "Hello quoted text"
            voice = "af_sarah"
            speed = 0.75f
            langCode = "es"
        }

        // When
        val failure = assertFailsWith<IOException> {
            track.process(mockk<LocalAudioTrackExecutor>())
        }

        // Then
        assertEquals("boom", failure.message)
        assertEquals("http://localhost:8880/v1/audio/speech", request.captured.uri.toString())
    }

    @Test
    fun `Given a KokoroAudioTrack When process with empty baseUrl Then throw IllegalStateException`() {
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
        // baseUrl is empty by default

        // When/Then
        assertFailsWith<IllegalStateException> {
            runTest {
                track.process(mockk())
            }
        }
    }
}
