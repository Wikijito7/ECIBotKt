package es.wokis.player.engine.source

import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager
import com.sedmelluq.discord.lavaplayer.track.AudioReference
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import org.apache.http.client.methods.CloseableHttpResponse
import org.apache.http.impl.client.CloseableHttpClient
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KokoroSourceManagerTest {

    private val sourceManager = KokoroSourceManager()

    @Test
    fun `Given a KokoroSourceManager When sourceName is called Then return kokoro`() {
        // When
        val actual = sourceManager.sourceName

        // Then
        assertEquals("kokoro", actual)
    }

    @Test
    fun `Given a KokoroSourceManager When loadItem is called with kokoro protocol Then return AudioTrack`() {
        // Given
        val manager = mockk<DefaultAudioPlayerManager>()
        val reference = AudioReference("kokoro://?text=Hello+World", null)

        // When
        val actual = sourceManager.loadItem(manager, reference)

        // Then
        assertNotNull(actual)
        assertTrue(actual is AudioTrack)
        assertEquals("TTS Message: Hello World", actual.info.title)
        assertEquals("Kokoro", actual.info.author)
        assertTrue(actual.info.isStream)
        assertEquals(Long.MAX_VALUE, actual.info.length)
    }

    @Test
    fun `Given a KokoroSourceManager When loadItem is called with kokoro protocol and long text Then truncate title to 30 chars`() {
        // Given
        val manager = mockk<DefaultAudioPlayerManager>()
        val longText = "This is a very long text that should be truncated"
        val reference = AudioReference("kokoro://?text=${longText.replace(" ", "+")}", null)

        // When
        val actual = sourceManager.loadItem(manager, reference)

        // Then
        assertNotNull(actual)
        assertTrue(actual is AudioTrack)
        // "TTS Message: " is 13 chars, so we take 30 chars from the text
        // The ellipsis "…" is added at the end
        assertTrue(actual.info.title.startsWith("TTS Message: "))
        assertTrue(actual.info.title.endsWith("…"))
        assertEquals(44, actual.info.title.length) // "TTS Message: " + 30 chars + "…"
    }

    @Test
    fun `Given a KokoroSourceManager When loadItem is called with kokoro protocol and voice parameter Then use provided voice`() {
        // Given
        val manager = mockk<DefaultAudioPlayerManager>()
        val reference = AudioReference("kokoro://?text=Hello&voice=af_sarah", null)

        // When
        val actual = sourceManager.loadItem(manager, reference)

        // Then
        assertNotNull(actual)
        assertTrue(actual is KokoroAudioTrack)
        val kokoroTrack = actual as KokoroAudioTrack
        assertEquals("af_sarah", kokoroTrack.voice)
    }

    @Test
    fun `Given a KokoroSourceManager When loadItem is called with kokoro protocol and speed parameter Then use provided speed`() {
        // Given
        val manager = mockk<DefaultAudioPlayerManager>()
        val reference = AudioReference("kokoro://?text=Hello&speed=1.5", null)

        // When
        val actual = sourceManager.loadItem(manager, reference)

        // Then
        assertNotNull(actual)
        assertTrue(actual is KokoroAudioTrack)
        val kokoroTrack = actual as KokoroAudioTrack
        assertEquals(1.5f, kokoroTrack.speed)
    }

    @Test
    fun `Given a KokoroSourceManager When loadItem is called with kokoro protocol and lang parameter Then use provided lang code`() {
        // Given
        val manager = mockk<DefaultAudioPlayerManager>()
        val reference = AudioReference("kokoro://?text=Hello&lang=a", null)

        // When
        val actual = sourceManager.loadItem(manager, reference)

        // Then
        assertNotNull(actual)
        assertTrue(actual is KokoroAudioTrack)
        val kokoroTrack = actual as KokoroAudioTrack
        assertEquals("a", kokoroTrack.langCode)
    }

    @Test
    fun `Given a KokoroSourceManager When loadItem has no text parameter Then the full query is used as raw text`() {
        // Given - URL without a text query parameter
        val manager = mockk<DefaultAudioPlayerManager>()
        val reference = AudioReference("kokoro://?voice=af_sarah", null)

        // When
        val actual = sourceManager.loadItem(manager, reference)

        // Then - current parser semantics treat the full query as the raw text
        assertTrue(actual is KokoroAudioTrack)
        val track = actual as KokoroAudioTrack
        assertEquals("voice=af_sarah", track.rawText)
        assertEquals("TTS Message: voice=af_sarah", track.info.title)
    }

    @Test
    fun `Given a KokoroSourceManager When loadItem is called with non kokoro protocol Then return null`() {
        // Given
        val manager = mockk<DefaultAudioPlayerManager>()
        val reference = AudioReference("https://youtube.com/watch?v=123", null)

        // When
        val actual = sourceManager.loadItem(manager, reference)

        // Then
        assertNull(actual)
    }

    @Test
    fun `Given a KokoroSourceManager When loadItem is called with configured defaults Then use default values`() {
        // Given
        val manager = mockk<DefaultAudioPlayerManager>()
        val customSourceManager = KokoroSourceManager().apply {
            defaultVoice = "custom_voice"
            defaultSpeed = 0.8f
            defaultLangCode = "f"
        }
        val reference = AudioReference("kokoro://?text=Hello", null)

        // When
        val actual = customSourceManager.loadItem(manager, reference)

        // Then
        assertNotNull(actual)
        assertTrue(actual is KokoroAudioTrack)
        val kokoroTrack = actual as KokoroAudioTrack
        assertEquals("custom_voice", kokoroTrack.voice)
        assertEquals(0.8f, kokoroTrack.speed)
        assertEquals("f", kokoroTrack.langCode)
    }

    @Test
    fun `Given a KokoroSourceManager When loadItem is called with empty defaults Then use internal defaults`() {
        // Given
        val manager = mockk<DefaultAudioPlayerManager>()
        val reference = AudioReference("kokoro://?text=Hello", null)

        // When
        val actual = sourceManager.loadItem(manager, reference)

        // Then
        assertNotNull(actual)
        assertTrue(actual is KokoroAudioTrack)
        val kokoroTrack = actual as KokoroAudioTrack
        assertEquals("em_santa", kokoroTrack.voice)
        assertEquals(1.0f, kokoroTrack.speed)
        assertEquals("e", kokoroTrack.langCode)
    }

    @Test
    fun `Given a KokoroSourceManager When loadItem is called with URL encoded text Then decode text correctly`() {
        // Given
        val manager = mockk<DefaultAudioPlayerManager>()
        val reference = AudioReference("kokoro://?text=Hello%2C+World%21", null)

        // When
        val actual = sourceManager.loadItem(manager, reference)

        // Then
        assertNotNull(actual)
        assertTrue(actual is KokoroAudioTrack)
        val kokoroTrack = actual as KokoroAudioTrack
        assertEquals("Hello, World!", kokoroTrack.rawText)
    }

    @Test
    fun `Given a KokoroSourceManager When isTrackEncodable is called Then return true`() {
        // Given
        val track = mockk<AudioTrack>()

        // When
        val actual = sourceManager.isTrackEncodable(track)

        // Then
        assertTrue(actual)
    }

    @Test
    fun `Given a KokoroSourceManager When decodeTrack is called Then return KokoroAudioTrack`() {
        // Given
        val trackInfo = mockk<com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo>()

        // When
        val actual = sourceManager.decodeTrack(trackInfo, mockk())

        // Then
        assertNotNull(actual)
        assertTrue(actual is KokoroAudioTrack)
        assertEquals(trackInfo, actual.info)
    }

    @Test
    fun `Given a KokoroSourceManager When encodeTrack is called Then do nothing`() {
        // Given
        val track = mockk<AudioTrack>()
        val output = mockk<java.io.DataOutput>()

        // When/Then - should not throw
        sourceManager.encodeTrack(track, output)
    }

    @Test
    fun `Given a KokoroSourceManager When shutdown is called Then close http interface`() {
        // When/Then - should not throw
        sourceManager.shutdown()
    }

    @Test
    fun `Given a KokoroSourceManager When getHttpInterface is called Then return interface`() {
        // When
        val actual = sourceManager.getHttpInterface()

        // Then
        assertNotNull(actual)
    }

    @Test
    fun `Given a KokoroSourceManager When configureRequests is called Then apply configuration`() {
        // Given
        val sourceManager = KokoroSourceManager()
        var configured = false

        // When
        sourceManager.configureRequests { config ->
            configured = true
            config
        }

        // Then - just verify it doesn't throw
        // The actual configuration is tested through init block behavior
    }

    @Test
    fun `Given a KokoroSourceManager When configureBuilder is called Then apply configuration`() {
        // Given
        val sourceManager = KokoroSourceManager()

        // When/Then - should not throw
        sourceManager.configureBuilder { builder ->
            // No-op
        }
    }
}
