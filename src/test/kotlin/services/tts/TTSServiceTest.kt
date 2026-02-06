package services.tts

import es.wokis.services.lavaplayer.GuildLavaPlayerService
import es.wokis.services.tts.TTSService
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class TTSServiceTest {

    private val ttsService = TTSService()

    @Test
    fun `Given message When loadAndPlayMessage is executed Then load and play tts`() = runTest {
        // Given
        val guildLavaPlayerService = mockk<GuildLavaPlayerService>()
        val originalMessage = "Hello world"
        val expectedUrl = "kokoro://?text=Hello%20world"

        coJustRun { guildLavaPlayerService.loadAndPlayTts(any()) }

        // When
        ttsService.loadAndPlayMessage(guildLavaPlayerService, originalMessage)

        // Then
        coVerify(exactly = 1) {
            guildLavaPlayerService.loadAndPlayTts(expectedUrl)
        }
    }

    @Test
    fun `Given message with spaces When loadAndPlayMessage is executed Then encode and play tts`() = runTest {
        // Given
        val guildLavaPlayerService = mockk<GuildLavaPlayerService>()
        val originalMessage = "Hello world test"
        val expectedUrl = "kokoro://?text=Hello%20world%20test"

        coJustRun { guildLavaPlayerService.loadAndPlayTts(any()) }

        // When
        ttsService.loadAndPlayMessage(guildLavaPlayerService, originalMessage)

        // Then
        coVerify(exactly = 1) {
            guildLavaPlayerService.loadAndPlayTts(expectedUrl)
        }
    }

    @Test
    fun `Given message with special characters When loadAndPlayMessage is executed Then encode and play tts`() = runTest {
        // Given
        val guildLavaPlayerService = mockk<GuildLavaPlayerService>()
        val originalMessage = "Hello, world!"
        val expectedUrl = "kokoro://?text=Hello%2C%20world%21"

        coJustRun { guildLavaPlayerService.loadAndPlayTts(any()) }

        // When
        ttsService.loadAndPlayMessage(guildLavaPlayerService, originalMessage)

        // Then
        coVerify(exactly = 1) {
            guildLavaPlayerService.loadAndPlayTts(expectedUrl)
        }
    }

    @Test
    fun `Given empty message When loadAndPlayMessage is executed Then load and play tts`() = runTest {
        // Given
        val guildLavaPlayerService = mockk<GuildLavaPlayerService>()
        val originalMessage = ""
        val expectedUrl = "kokoro://?text="

        coJustRun { guildLavaPlayerService.loadAndPlayTts(any()) }

        // When
        ttsService.loadAndPlayMessage(guildLavaPlayerService, originalMessage)

        // Then
        coVerify(exactly = 1) {
            guildLavaPlayerService.loadAndPlayTts(expectedUrl)
        }
    }
}
