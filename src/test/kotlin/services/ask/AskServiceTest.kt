package services.ask

import es.wokis.services.ask.AIProvider
import es.wokis.services.ask.AskService
import es.wokis.services.config.ConfigService
import es.wokis.services.lavaplayer.GuildLavaPlayerService
import es.wokis.services.tts.TTSService
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AskServiceTest {

    private val aiProvider: AIProvider = mockk()
    private val ttsService: TTSService = mockk()
    private val configService: ConfigService = mockk {
        every { config } returns mockk {
            every { ask } returns mockk {
                every { model } returns "test-model"
            }
        }
    }

    private val askService = AskService(aiProvider, ttsService, configService)

    @Test
    fun `Given prompt When ask is called Then return AI answer`() = runTest {
        // Given
        coEvery { aiProvider.ask("hello", "test-model") } returns "hi there"

        // When
        val result = askService.ask("hello")

        // Then
        assertEquals("hi there", result)
    }

    @Test
    fun `Given prompt When askAndPlayTTS is called Then return answer and play TTS`() = runTest {
        // Given
        val guildLavaPlayerService: GuildLavaPlayerService = mockk()
        coEvery { aiProvider.ask("hello", "test-model") } returns "hi there"
        coJustRun { ttsService.loadAndPlayMessage(any(), any()) }

        // When
        val result = askService.askAndPlayTTS("hello", guildLavaPlayerService)

        // Then
        assertEquals("hi there", result)
        coVerify(exactly = 1) {
            aiProvider.ask("hello", "test-model")
            ttsService.loadAndPlayMessage(guildLavaPlayerService, "hi there")
        }
    }
}
