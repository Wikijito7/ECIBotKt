package services.lavaplayer

import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager
import es.wokis.services.config.ConfigService
import es.wokis.services.config.youtubeOauth2Token
import es.wokis.services.lavaplayer.AudioPlayerManagerProvider
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

class AudioPlayerManagerProviderTest {

    private val config = mockk<ConfigService>()
    private val audioPlayerManagerProvider: AudioPlayerManagerProvider = AudioPlayerManagerProvider(config)

    @Test
    fun `When createAudioPlayerManager is called Then return DefaultAudioPlayerManager`() {
        // Given
        every { config.youtubeOauth2Token } returns ""

        // When
        val actual = audioPlayerManagerProvider.createAudioPlayerManager()

        // Then
        assertTrue(actual is DefaultAudioPlayerManager)
    }
}
