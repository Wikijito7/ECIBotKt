package commands.reconnect

import dev.kord.common.Locale
import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.core.behavior.interaction.response.DeferredPublicMessageInteractionResponseBehavior
import dev.kord.core.entity.interaction.ChatInputCommandInteraction
import dev.kord.core.supplier.EntitySupplyStrategy
import es.wokis.commands.reconnect.ReconnectCommand
import es.wokis.services.lavaplayer.GuildLavaPlayerService
import es.wokis.services.localization.LocalizationService
import es.wokis.services.queue.GuildQueueService
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ReconnectCommandTest {
    private val guildQueueService: GuildQueueService = mockk()
    private val localizationService: LocalizationService = mockk()

    private val reconnectCommand = ReconnectCommand(
        guildQueueService = guildQueueService,
        localizationService = localizationService
    )

    @Test
    fun `Given bot not connected When onExecute is called Then respond with not connected message`() = runTest {
        // Given
        val mockKord: Kord = mockk {
            every { resources } returns mockk {
                every { defaultStrategy } returns EntitySupplyStrategy.rest
            }
            every { defaultSupplier } returns mockk()
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
            every { isConnected() } returns false
            coJustRun { reconnect() }
        }

        coEvery { guildQueueService.getOrCreateLavaPlayerService(any()) } returns guildLavaPlayerService
        coEvery { localizationService.getString(any(), any(), any()) } returns "NotConnectedMessage"

        // When
        reconnectCommand.onExecute(interaction, response)

        // Then
        coVerify(exactly = 0) {
            guildLavaPlayerService.reconnect()
        }
    }

    @Test
    fun `Given bot connected When onExecute is called Then reconnect and respond with success message`() = runTest {
        // Given
        val mockKord: Kord = mockk {
            every { resources } returns mockk {
                every { defaultStrategy } returns EntitySupplyStrategy.rest
            }
            every { defaultSupplier } returns mockk()
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
            every { isConnected() } returns true
            coJustRun { reconnect() }
        }

        coEvery { guildQueueService.getOrCreateLavaPlayerService(any()) } returns guildLavaPlayerService
        coEvery { localizationService.getString(any(), any(), any()) } returns "ReconnectSuccess"

        // When
        reconnectCommand.onExecute(interaction, response)

        // Then
        coVerify(exactly = 1) {
            guildLavaPlayerService.reconnect()
        }
    }

    @Test
    fun `Given bot connected but reconnect throws exception When onExecute is called Then throw exception`() = runTest {
        // Given
        val mockKord: Kord = mockk {
            every { resources } returns mockk {
                every { defaultStrategy } returns EntitySupplyStrategy.rest
            }
            every { defaultSupplier } returns mockk()
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
            every { isConnected() } returns true
            coEvery { reconnect() } throws IllegalStateException("Reconnect failed")
        }

        coEvery { guildQueueService.getOrCreateLavaPlayerService(any()) } returns guildLavaPlayerService

        // When/Then - exception is thrown (handled by CommandHandlerService)
        assertThrows<IllegalStateException> {
            reconnectCommand.onExecute(interaction, response)
        }

        coVerify(exactly = 1) {
            guildLavaPlayerService.reconnect()
        }
    }
}
