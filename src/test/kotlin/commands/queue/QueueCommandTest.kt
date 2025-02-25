package commands.queue

import dev.kord.common.Locale
import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.core.behavior.interaction.response.DeferredPublicMessageInteractionResponseBehavior
import dev.kord.core.entity.interaction.ButtonInteraction
import dev.kord.core.entity.interaction.ChatInputCommandInteraction
import dev.kord.core.supplier.EntitySupplyStrategy
import es.wokis.commands.ComponentsEnum
import es.wokis.commands.queue.QueueCommand
import es.wokis.services.lavaplayer.GuildLavaPlayerService
import es.wokis.services.localization.LocalizationService
import es.wokis.services.queue.GuildQueueService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class QueueCommandTest {

    private val guildQueueService: GuildQueueService = mockk()
    private val localizationService: LocalizationService = mockk()

    private val queueCommand = QueueCommand(
        guildQueueService = guildQueueService,
        localizationService = localizationService
    )

    @Test
    fun `Given interaction When onExecute Then respond with queue message`() = runTest {
        // Given
        val mockKord: Kord = mockk {
            every { resources } returns mockk {
                every { defaultStrategy } returns EntitySupplyStrategy.rest
            }
            every { defaultSupplier } returns mockk()
            coEvery { getGuild(any()) } returns mockk {
                every { name } returns "TestGuild"
            }
            every { rest } returns mockk {
                every { interaction } returns mockk(relaxed = true)
            }
        }
        val interaction = mockk<ChatInputCommandInteraction> {
            every { guildLocale } returns Locale.BULGARIAN
            every { data } returns mockk {
                every { guildId.value } returns Snowflake(123)
            }
            every { kord } returns mockKord
        }
        val response = mockk<DeferredPublicMessageInteractionResponseBehavior> {
            every { kord } returns mockKord
            every { applicationId } returns Snowflake(456)
            every { token } returns "testToken"
        }
        val guildLavaPlayerService: GuildLavaPlayerService = mockk {
            every { getQueue() } returns listOf()
        }

        coEvery { guildQueueService.getOrCreateLavaPlayerService(any()) } returns guildLavaPlayerService
        every { localizationService.getString(any(), any()) } returns "TestMessage"
        every { localizationService.getStringFormat(any(), any(), *anyVararg()) } returns "Format"

        // When
        queueCommand.onExecute(interaction, response)

        // Then
        coVerify(exactly = 1) {
            guildLavaPlayerService.getQueue()
        }
    }

    @Test
    fun `Given interaction When onInteract Then respond with queue message`() = runTest {
        // Given
        val mockKord: Kord = mockk {
            every { resources } returns mockk {
                every { defaultStrategy } returns EntitySupplyStrategy.rest
            }
            every { defaultSupplier } returns mockk()
            coEvery { getGuild(any()) } returns mockk {
                every { name } returns "TestGuild"
            }
            every { rest } returns mockk {
                every { interaction } returns mockk(relaxed = true)
            }
        }
        val interaction = mockk<ButtonInteraction> {
            every { guildLocale } returns Locale.BULGARIAN
            every { data } returns mockk {
                every { guildId.value } returns Snowflake(123)
            }
            every { kord } returns mockKord
            every { component } returns mockk {
                every { customId } returns ComponentsEnum.QUEUE_PREVIOUS.customId
            }
            every { message } returns mockk(relaxed = true)
        }
        val guildLavaPlayerService: GuildLavaPlayerService = mockk {
            every { getQueue() } returns listOf()
        }

        coEvery { guildQueueService.getLavaPlayerService(any()) } returns guildLavaPlayerService
        every { localizationService.getString(any(), any()) } returns "TestMessage"
        every { localizationService.getStringFormat(any(), any(), *anyVararg()) } returns "Format"

        // When
        queueCommand.onInteract(interaction)

        // Then
        coVerify(exactly = 1) {
            guildLavaPlayerService.getQueue()
        }
    }
}
