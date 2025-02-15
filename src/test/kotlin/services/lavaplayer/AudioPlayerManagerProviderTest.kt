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
        every { configService.config.youtube.oauth2Token } returns ""

        // When
        val actual = audioPlayerManagerProvider.createAudioPlayerManager()

        // Then
        assertTrue(actual is DefaultAudioPlayerManager)
    }
}
