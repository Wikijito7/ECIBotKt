package services.lavaplayer

import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager
import dev.kord.core.behavior.channel.BaseVoiceChannelBehavior
import dev.kord.core.entity.channel.MessageChannel
import es.wokis.services.lavaplayer.GuildLavaPlayerService
import es.wokis.services.localization.LocalizationService
import io.mockk.*
import mock.TestDispatchers
import org.junit.jupiter.api.Test
import kotlin.test.Ignore

class GuildLavaPlayerServiceTest {

    private val appDispatchers = TestDispatchers()
    private val textChannel: MessageChannel = mockk()
    private val voiceChannel: BaseVoiceChannelBehavior = mockk()
    private val audioPlayerManager: AudioPlayerManager = mockk {
        every { createPlayer() } returns mockk {
            justRun { addListener(any()) }
        }
    }
    private val localizationService: LocalizationService = mockk()

    private val playerService = GuildLavaPlayerService(
        appDispatchers = appDispatchers,
        textChannel = textChannel,
        voiceChannel = voiceChannel,
        audioPlayerManager = audioPlayerManager,
        localizationService = localizationService
    )

    @Ignore("Cannot test atm")
    @Test
    fun `Given player service When loadAndPlay is called Then loadItem`() {
        // Given
        val url = "https://manolete.lol/yes"
        every { audioPlayerManager.loadItem(any<String>(), any()) } returns null

        // When
        playerService.loadAndPlay(url)

        // Then
        coVerify(exactly = 1) {
            textChannel.createMessage(any<String>())
        }
    }
}
