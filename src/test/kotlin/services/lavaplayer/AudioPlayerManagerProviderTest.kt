package services.lavaplayer

import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager
import es.wokis.services.config.ConfigService
import es.wokis.services.lavaplayer.AudioPlayerManagerProvider
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

class AudioPlayerManagerProviderTest {

    private val configService = mockk<ConfigService>()
    private val audioPlayerManagerProvider = AudioPlayerManagerProvider(configService)

    @Test
    fun `When createAudioPlayerManager is called Then return DefaultAudioPlayerManager`() {
        // Given
        every { configService.config.youtube.oauth2Token } returns null
        every { configService.config.deezer.enabled } returns false
        every { configService.config.spotify.enabled } returns false
        every { configService.config.youtube.poToken } returns null
        every { configService.config.youtube.visitorData } returns null

        // When
        val actual = audioPlayerManagerProvider.createAudioPlayerManager()

        // Then
        assertTrue(actual is DefaultAudioPlayerManager)
    }
}
