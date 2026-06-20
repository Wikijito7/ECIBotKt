package services.lavaplayer

import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager
import es.wokis.services.config.ConfigService
import es.wokis.services.lavaplayer.AudioPlayerManagerProvider
import es.wokis.services.lavaplayer.LyricsService
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

class AudioPlayerManagerProviderTest {

    private val configService = mockk<ConfigService>()
    private val lyricsService = mockk<LyricsService> {
        justRun { registerLyricsManager(any()) }
    }
    private val audioPlayerManagerProvider = AudioPlayerManagerProvider(configService, lyricsService)

    @Test
    fun `When createAudioPlayerManager is called Then return DefaultAudioPlayerManager`() {
        // Given
        every { configService.config.youtube.oauth2Token } returns null
        every { configService.config.deezer.enabled } returns true
        every { configService.config.deezer.masterDecryptionKey } returns "master_key"
        every { configService.config.deezer.arlToken } returns "altoke"
        every { configService.config.spotify.enabled } returns true
        every { configService.config.spotify.clientId } returns "client_id"
        every { configService.config.spotify.clientSecret } returns "client_secret"
        every { configService.config.spotify.customEndpoint } returns ""
        every { configService.config.tidal.enabled } returns true
        every { configService.config.tidal.countryCode } returns "ES"
        every { configService.config.tidal.token } returns "altoke"
        every { configService.config.youtube.enabled } returns true
        every { configService.config.youtube.poToken } returns null
        every { configService.config.youtube.visitorData } returns null
        every { configService.config.youtube.remoteCipherUrl } returns null
        every { configService.config.youtube.remoteCipherPassword } returns null
        every { configService.config.kokoro.baseUrl } returns ""
        every { configService.config.kokoro.defaultVoice } returns ""
        every { configService.config.kokoro.defaultSpeed } returns 1.0f
        every { configService.config.kokoro.defaultLangCode } returns ""

        // When
        val actual = audioPlayerManagerProvider.createAudioPlayerManager()

        // Then
        assertTrue(actual is DefaultAudioPlayerManager)
    }
}
