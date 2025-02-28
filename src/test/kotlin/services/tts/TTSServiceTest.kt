package services.tts

import es.wokis.domain.GetFloweryVoicesUseCase
import es.wokis.services.lavaplayer.GuildLavaPlayerService
import es.wokis.services.tts.TTSService
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import kotlin.test.Test

class TTSServiceTest {

    private val getFloweryVoicesUseCase = mockk<GetFloweryVoicesUseCase>()

    private val ttsService = TTSService(
        getFloweryVoicesUseCase = getFloweryVoicesUseCase
    )

    @Test
    fun `Given message with length less than 2048 When loadAndPlayMessage is executed Then load and play tts`() = runTest {
        // Given
        val guildLavaPlayerService = mockk<GuildLavaPlayerService>()
        val originalMessage = "a".repeat(2047)
        val voice = "voice"
        val encodedMessage = "ftts://$originalMessage?voice=$voice"
        val messages = listOf(encodedMessage)

        coEvery { getFloweryVoicesUseCase() } returns listOf(voice)
        coJustRun { guildLavaPlayerService.loadAndPlayTts(any()) }

        // When
        ttsService.loadAndPlayMessage(guildLavaPlayerService, originalMessage)

        // Then
        coVerify(exactly = 1) {
            guildLavaPlayerService.loadAndPlayTts(messages)
        }
    }

    @ParameterizedTest(name = "Given message with length greater than 2048 separated by {1} When loadAndPlayMessage is executed Then load and play tts")
    @MethodSource("getData")
    fun `Given message with length greater than 2048 When loadAndPlayMessage is executed Then load and play tts`(candidate: String, separator: String) = runTest {
        // Given
        val guildLavaPlayerService = mockk<GuildLavaPlayerService>()
        val messageSplit = candidate.split(separator)
        val firstPart = messageSplit.first()
        val secondPart = messageSplit[1]
        val voice = "voice"
        val firstEncodedMessage = "ftts://$firstPart?voice=$voice"
        val secondEncodedMessage = "ftts://$secondPart?voice=$voice"
        val messages = listOf(firstEncodedMessage, secondEncodedMessage)

        coEvery { getFloweryVoicesUseCase() } returns listOf(voice)
        coJustRun { guildLavaPlayerService.loadAndPlayTts(any()) }

        // When
        ttsService.loadAndPlayMessage(guildLavaPlayerService, candidate)

        // Then
        coVerify(exactly = 1) {
            guildLavaPlayerService.loadAndPlayTts(messages)
        }
    }

    @Test
    fun `Given message with length greater than 2048 with two separators When loadAndPlayMessage is executed Then load and play tts`() = runTest {
        // Given
        val guildLavaPlayerService = mockk<GuildLavaPlayerService>()
        val firstPart = "a".repeat(1200)
        val secondPart = "a".repeat(1300)
        val thirdPart = "b".repeat(1200)
        val originalMessage = "$firstPart\n$secondPart.$thirdPart"
        val voice = "voice"
        val firstEncodedMessage = "ftts://$firstPart?voice=$voice"
        val secondEncodedMessage = "ftts://$secondPart?voice=$voice"
        val thirdEncodedMessage = "ftts://$thirdPart?voice=$voice"
        val messages = listOf(firstEncodedMessage, secondEncodedMessage, thirdEncodedMessage)

        coEvery { getFloweryVoicesUseCase() } returns listOf(voice)
        coJustRun { guildLavaPlayerService.loadAndPlayTts(any()) }

        // When
        ttsService.loadAndPlayMessage(guildLavaPlayerService, originalMessage)

        // Then
        coVerify(exactly = 1) {
            guildLavaPlayerService.loadAndPlayTts(messages)
        }
    }

    @Test
    fun `Given message with length greater than 2048 separated by new line without voice When loadAndPlayMessage is executed Then load and play tts`() = runTest {
        // Given
        val guildLavaPlayerService = mockk<GuildLavaPlayerService>()
        val firstPart = "a".repeat(1200)
        val secondPart = "a".repeat(1300)
        val originalMessage = firstPart + "\n" + secondPart
        val firstEncodedMessage = "ftts://$firstPart"
        val secondEncodedMessage = "ftts://$secondPart"
        val messages = listOf(firstEncodedMessage, secondEncodedMessage)

        coEvery { getFloweryVoicesUseCase() } returns emptyList()
        coJustRun { guildLavaPlayerService.loadAndPlayTts(any()) }

        // When
        ttsService.loadAndPlayMessage(guildLavaPlayerService, originalMessage)

        // Then
        coVerify(exactly = 1) {
            guildLavaPlayerService.loadAndPlayTts(messages)
        }
    }

    companion object {
        @JvmStatic
        fun getData(): List<Arguments> = listOf(
            Arguments.of("a".repeat(1200) + "\n" + "a".repeat(1300), "\n"),
            Arguments.of("a".repeat(1200) + "." + "a".repeat(1300), "."),
            Arguments.of("a".repeat(1200) + "," + "a".repeat(1300), ","),
            Arguments.of("a".repeat(1200) + " " + "a".repeat(1300), " "),
        )
    }
}
