package commands.queue

import dev.kord.common.Locale
import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.core.behavior.interaction.response.DeferredPublicMessageInteractionResponseBehavior
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.entity.interaction.ChatInputCommandInteraction
import dev.kord.core.supplier.EntitySupplyStrategy
import dev.kord.rest.builder.message.modify.InteractionResponseModifyBuilder
import es.wokis.commands.queue.QueueCommand
import es.wokis.services.lavaplayer.GuildLavaPlayerService
import es.wokis.services.localization.LocalizationService
import es.wokis.services.queue.GuildQueueService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import mock.mockedDiscordMessage
import org.junit.jupiter.api.Test
import kotlin.test.Ignore

class QueueCommandTest {

    private val guildQueueService: GuildQueueService = mockk()
    private val localizationService: LocalizationService = mockk()

    private val queueCommand = QueueCommand(
        guildQueueService = guildQueueService,
        localizationService = localizationService
    )

    /*
     * Receiver class kotlin.jvm.functions.Function1$Subclass4 does not define or inherit an
     * implementation of the resolved method 'abstract java.lang.Object invoke(java.lang.Object)'
     * of interface kotlin.jvm.functions.Function1.
     *
     * modifyInteractionResponse(..., builder: InteractionResponseModifyBuilder.() -> Unit)
     */
    @Test
    @Ignore("Fails trying to test")
    fun `Given interaction When onExecute Then respond with queue message`() = runTest {
        // Given
        val slot = slot<InteractionResponseModifyBuilder.() -> Unit>()

        val mockKord: Kord = mockk {
            every { resources } returns mockk {
                every { defaultStrategy } returns EntitySupplyStrategy.rest
            }
            every { defaultSupplier } returns mockk()
            coEvery { getGuild(any()) } returns mockk {
                every { name } returns "TestGuild"
            }
            every { rest } returns mockk {
                every { interaction } returns mockk {
                    coEvery {
                        modifyInteractionResponse(any(), any(), capture(slot))
                    } answers {
                        val builder = mockk<InteractionResponseModifyBuilder>(relaxed = true)
                        slot.captured.invoke(builder)
                        mockedDiscordMessage
                    }
                }
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
            response.respond(any())
        }
    }
}
