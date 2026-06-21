package commands.ask

import dev.kord.common.Locale
import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.interaction.response.DeferredPublicMessageInteractionResponseBehavior
import dev.kord.core.entity.Message
import dev.kord.core.entity.interaction.MessageCommandInteraction
import es.wokis.commands.ask.AskContextMenuCommand
import es.wokis.localization.LocalizationKeys
import es.wokis.services.ask.AskExecutor
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

class AskContextMenuCommandTest {

    private val askExecutor = mockk<AskExecutor>()
    private val localizationService = mockk<LocalizationService>()
    private val guildQueueService = mockk<GuildQueueService>()

    private val askContextMenuCommand = AskContextMenuCommand(
        askExecutor = askExecutor,
        localizationService = localizationService,
        guildQueueService = guildQueueService
    )

    @Test
    fun `Given interaction When onExecute is called Then ask AI with message content and play TTS`() = runTest {
        // Given
        val targetMessage = mockk<Message> {
            every { content } returns "hello from context menu"
        }
        val interaction = mockk<MessageCommandInteraction> {
            every { guildLocale } returns Locale.ENGLISH_UNITED_STATES
            every { data } returns mockk {
                every { guildId.value } returns Snowflake(123)
            }
            coEvery { getTarget() } returns targetMessage
        }
        val response = mockk<DeferredPublicMessageInteractionResponseBehavior>(relaxed = true)
        val guildLavaPlayerService: GuildLavaPlayerService = mockk()

        coEvery { guildQueueService.getOrCreateLavaPlayerService(any()) } returns guildLavaPlayerService
        coJustRun { askExecutor.execute(any(), any(), any(), any(), any()) }

        // When
        askContextMenuCommand.onExecute(interaction, response)

        // Then
        coVerify(exactly = 1) {
            askExecutor.execute("hello from context menu", Snowflake(123), Locale.ENGLISH_UNITED_STATES, response, guildLavaPlayerService)
        }
    }

    @Test
    fun `Given interaction with blank message When onExecute is called Then show error`() = runTest {
        // Given
        val targetMessage = mockk<Message> {
            every { content } returns ""
        }
        val interaction = mockk<MessageCommandInteraction> {
            every { guildLocale } returns Locale.ENGLISH_UNITED_STATES
            every { data } returns mockk {
                every { guildId.value } returns Snowflake(123)
            }
            coEvery { getTarget() } returns targetMessage
        }
        val response = mockk<DeferredPublicMessageInteractionResponseBehavior>(relaxed = true)

        coEvery { localizationService.getString(eq(LocalizationKeys.ERROR_NO_CONTENT_PROVIDED), any(), any()) } returns ""

        // When
        askContextMenuCommand.onExecute(interaction, response)

        // Then
        coVerify(exactly = 0) {
            askExecutor.execute(any(), any(), any(), any(), any())
        }
    }
}
