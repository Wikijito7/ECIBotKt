package commands.ask

import dev.kord.common.Locale
import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.interaction.response.DeferredPublicMessageInteractionResponseBehavior
import dev.kord.core.entity.interaction.ChatInputCommandInteraction
import dev.kord.core.entity.interaction.response.PublicMessageInteractionResponse
import es.wokis.commands.ask.AskCommand
import es.wokis.localization.LocalizationKeys
import es.wokis.services.ask.AskService
import es.wokis.services.config.ConfigService
import es.wokis.services.lavaplayer.GuildLavaPlayerService
import es.wokis.services.localization.LocalizationService
import es.wokis.services.queue.GuildQueueService
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class AskCommandTest {

    private val askService = mockk<AskService>()
    private val localizationService = mockk<LocalizationService>()
    private val guildQueueService = mockk<GuildQueueService>()
    private val configService: ConfigService = mockk {
        every { config } returns mockk {
            every { ask } returns mockk {
                every { enabled } returns true
            }
        }
    }

    private val askCommand = AskCommand(
        askService = askService,
        localizationService = localizationService,
        guildQueueService = guildQueueService,
        configService = configService
    )

    @Test
    fun `Given interaction When onExecute is called Then ask AI and play TTS`() = runTest {
        // Given
        val interaction = mockk<ChatInputCommandInteraction> {
            every { guildLocale } returns Locale.ENGLISH_UNITED_STATES
            every { data } returns mockk {
                every { guildId.value } returns Snowflake(123)
            }
            every { command } returns mockk {
                every { strings } returns mapOf("prompt" to "hello")
            }
        }
        val response = mockk<DeferredPublicMessageInteractionResponseBehavior>(relaxed = true)
        val guildLavaPlayerService: GuildLavaPlayerService = mockk()

        coEvery { guildQueueService.getOrCreateLavaPlayerService(any()) } returns guildLavaPlayerService
        coEvery { localizationService.getString(eq(LocalizationKeys.ASK_THINKING), any(), any()) } returns "Thinking..."
        coEvery { askService.askAndPlayTTS(any(), any()) } returns "Hello, monkey!"

        // When
        askCommand.onExecute(interaction, response)

        // Then
        coVerify(exactly = 1) {
            askService.askAndPlayTTS("hello", guildLavaPlayerService)
        }
    }

    @Test
    fun `Given interaction when ask not enabled When onExecute is called Then show error`() = runTest {
        // Given
        val disabledConfig: ConfigService = mockk {
            every { config } returns mockk {
                every { ask } returns mockk {
                    every { enabled } returns false
                }
            }
        }
        val cmd = AskCommand(
            askService = askService,
            localizationService = localizationService,
            guildQueueService = guildQueueService,
            configService = disabledConfig
        )
        val interaction = mockk<ChatInputCommandInteraction> {
            every { guildLocale } returns Locale.ENGLISH_UNITED_STATES
            every { data } returns mockk {
                every { guildId.value } returns Snowflake(123)
            }
            every { command } returns mockk {
                every { strings } returns mapOf("prompt" to "hello")
            }
        }
        val response = mockk<DeferredPublicMessageInteractionResponseBehavior>(relaxed = true)

        coEvery { localizationService.getString(eq(LocalizationKeys.ASK_NOT_ENABLED), any(), any()) } returns "Not enabled"

        // When
        cmd.onExecute(interaction, response)

        // Then
        coVerify(exactly = 0) {
            askService.askAndPlayTTS(any(), any())
        }
    }

    @Test
    fun `Given interaction with wrong argument When onExecute is called Then show error`() = runTest {
        // Given
        val interaction = mockk<ChatInputCommandInteraction> {
            every { guildLocale } returns Locale.ENGLISH_UNITED_STATES
            every { data } returns mockk {
                every { guildId.value } returns Snowflake(123)
            }
            every { command } returns mockk {
                every { strings } returns mapOf("other" to "hello")
            }
        }
        val response = mockk<DeferredPublicMessageInteractionResponseBehavior>(relaxed = true)

        coEvery { localizationService.getStringFormat(any(), any(), any(), *anyVararg()) } returns "Error"

        // When
        askCommand.onExecute(interaction, response)

        // Then
        coVerify(exactly = 0) {
            askService.askAndPlayTTS(any(), any())
        }
    }
}
