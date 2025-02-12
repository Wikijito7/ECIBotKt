package lavaplayer

import dev.kord.core.behavior.channel.BaseVoiceChannelBehavior
import dev.kord.core.entity.channel.MessageChannel
import es.wokis.services.lavaplayer.GuildLavaPlayerService
import es.wokis.services.lavaplayer.PlayerService
import es.wokis.utils.Log
import io.mockk.*
import mock.TestDispatchers
import org.junit.jupiter.api.Test
import kotlin.test.Ignore

class GuildLavaPlayerServiceTest {

    private val appDispatchers = TestDispatchers()
    private val youtubeOauth2Token = null
    private val textChannel: MessageChannel = mockk()
    private val voiceChannel: BaseVoiceChannelBehavior = mockk()

    private val playerService: PlayerService = GuildLavaPlayerService(
        appDispatchers = appDispatchers,
        youtubeOauth2Token = youtubeOauth2Token,
        textChannel = textChannel,
        voiceChannel = voiceChannel
    )

    @Ignore("Test fails")
    @Test
    fun `Given player service When loadAndPlay is called Then loadItem`() {
        // Given
        mockkStatic(Log::class)
        mockkStatic(textChannel::createMessage)
        val url = "https://manolete.lol/yes"
        coEvery { textChannel.createMessage(any<String>()) } returns mockk()

        // When
        playerService.loadAndPlay(url)

        // Then
        coVerify(exactly = 1) {
            Log.error(any<String>())
            textChannel.createMessage(any<String>())
        }
    }
}
