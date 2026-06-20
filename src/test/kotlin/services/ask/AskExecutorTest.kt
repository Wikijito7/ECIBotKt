package services.ask

import dev.kord.common.Locale
import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.interaction.response.DeferredPublicMessageInteractionResponseBehavior
import es.wokis.localization.LocalizationKeys
import es.wokis.services.ask.AskExecutor
import es.wokis.services.ask.AskService
import es.wokis.services.config.ConfigService
import es.wokis.services.lavaplayer.GuildLavaPlayerService
import es.wokis.services.localization.LocalizationService
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class AskExecutorTest {

    private val askService = mockk<AskService>()
    private val localizationService = mockk<LocalizationService>()
    private val configService: ConfigService = mockk {
        every { config } returns mockk {
            every { ask } returns mockk {
                every { enabled } returns true
            }
        }
    }

    private val askExecutor = AskExecutor(askService, localizationService, configService)

    @Test
    fun `Given prompt When execute is called Then ask AI and play TTS`() = runTest {
        // Given
        val response = mockk<DeferredPublicMessageInteractionResponseBehavior>(relaxed = true)
        val guildLavaPlayerService: GuildLavaPlayerService = mockk()

        coEvery { localizationService.getString(eq(LocalizationKeys.ASK_THINKING), any(), any()) } returns "Thinking..."
        coEvery { askService.askAndPlayTTS(any(), any()) } returns "Hello, monkey!"

        // When
        askExecutor.execute("hello", Snowflake(123), Locale.ENGLISH_UNITED_STATES, response, guildLavaPlayerService)

        // Then
        coVerify(exactly = 1) {
            askService.askAndPlayTTS("hello", guildLavaPlayerService)
        }
    }

    @Test
    fun `Given prompt when ask not enabled When execute is called Then show error`() = runTest {
        // Given
        val disabledExecutor = AskExecutor(
            askService,
            localizationService,
            mockk {
                every { config } returns mockk {
                    every { ask } returns mockk {
                        every { enabled } returns false
                    }
                }
            }
        )
        val response = mockk<DeferredPublicMessageInteractionResponseBehavior>(relaxed = true)
        val guildLavaPlayerService: GuildLavaPlayerService = mockk()

        coEvery { localizationService.getString(eq(LocalizationKeys.ASK_NOT_ENABLED), any(), any()) } returns "Not enabled"

        // When
        disabledExecutor.execute("hello", Snowflake(123), Locale.ENGLISH_UNITED_STATES, response, guildLavaPlayerService)

        // Then
        coVerify(exactly = 0) {
            askService.askAndPlayTTS(any(), any())
        }
    }

    @Test
    fun `Given prompt When API fails Then show error`() = runTest {
        // Given
        val response = mockk<DeferredPublicMessageInteractionResponseBehavior>(relaxed = true)
        val guildLavaPlayerService: GuildLavaPlayerService = mockk()

        coEvery { localizationService.getString(any(), any(), any()) } returns "message"
        coEvery { askService.askAndPlayTTS(any(), any()) } throws RuntimeException("API error")

        // When
        askExecutor.execute("hello", Snowflake(123), Locale.ENGLISH_UNITED_STATES, response, guildLavaPlayerService)

        // Then
        coVerify(exactly = 1) {
            askService.askAndPlayTTS("hello", guildLavaPlayerService)
        }
    }
}
