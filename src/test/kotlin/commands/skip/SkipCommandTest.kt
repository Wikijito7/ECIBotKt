package commands.skip

import commands.discordMessage
import dev.kord.common.Locale
import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.core.behavior.interaction.response.DeferredPublicMessageInteractionResponseBehavior
import dev.kord.core.entity.interaction.ChatInputCommandInteraction
import dev.kord.core.supplier.EntitySupplyStrategy
import es.wokis.commands.skip.SkipCommand
import es.wokis.services.lavaplayer.GuildLavaPlayerService
import es.wokis.services.localization.LocalizationService
import es.wokis.services.queue.GuildQueueService
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class SkipCommandTest {

    private val guildQueueService: GuildQueueService = mockk()
    private val localizationService: LocalizationService = mockk()

    private val skipCommand = SkipCommand(
        guildQueueService = guildQueueService,
        localizationService = localizationService
    )

    @Test
    fun `Given interaction When onExecute is called Then execute skip command`() = runTest {
        // Given
        val mockKord: Kord = mockk {
            every { resources } returns mockk {
                every { defaultStrategy } returns EntitySupplyStrategy.rest
            }
            every { defaultSupplier } returns mockk()
            every { rest } returns mockk {
                every { interaction } returns mockk {
                    coEvery {
                        modifyInteractionResponse(
                            any(),
                            any(),
                            any<dev.kord.rest.json.request.InteractionResponseModifyRequest>(),
                        )
                    } returns discordMessage()
                    coEvery {
                        modifyInteractionResponse(
                            any(),
                            any(),
                            any<dev.kord.rest.json.request.MultipartInteractionResponseModifyRequest>(),
                        )
                    } returns discordMessage()
                }
                every { channel } returns mockk {
                    coEvery {
                        editMessage(any(), any(), any<dev.kord.rest.json.request.MessageEditPatchRequest>())
                    } returns discordMessage()
                    coEvery {
                        editMessage(any(), any(), any<dev.kord.rest.json.request.MultipartMessagePatchRequest>())
                    } returns discordMessage()
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
            coJustRun { skip() }
        }

        coEvery { guildQueueService.getOrCreateLavaPlayerService(any()) } returns guildLavaPlayerService
        coEvery { localizationService.getString(any(), any(), any()) } returns "TestMessage"

        // When
        skipCommand.onExecute(interaction, response)

        // Then
        coVerify(exactly = 1) {
            guildLavaPlayerService.skip()
        }
    }

    @Test
    fun `Given invalid interaction When onExecute is called Then throw exception`() = runTest {
        // Given
        val mockKord: Kord = mockk {
            every { resources } returns mockk {
                every { defaultStrategy } returns EntitySupplyStrategy.rest
            }
            every { defaultSupplier } returns mockk()
            every { rest } returns mockk {
                every { interaction } returns mockk {
                    coEvery {
                        modifyInteractionResponse(
                            any(),
                            any(),
                            any<dev.kord.rest.json.request.InteractionResponseModifyRequest>(),
                        )
                    } returns discordMessage()
                    coEvery {
                        modifyInteractionResponse(
                            any(),
                            any(),
                            any<dev.kord.rest.json.request.MultipartInteractionResponseModifyRequest>(),
                        )
                    } returns discordMessage()
                }
                every { channel } returns mockk {
                    coEvery {
                        editMessage(any(), any(), any<dev.kord.rest.json.request.MessageEditPatchRequest>())
                    } returns discordMessage()
                    coEvery {
                        editMessage(any(), any(), any<dev.kord.rest.json.request.MultipartMessagePatchRequest>())
                    } returns discordMessage()
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
            coJustRun { skip() }
        }
        coEvery { guildQueueService.getOrCreateLavaPlayerService(any()) } throws IllegalStateException()
        coEvery { localizationService.getString(any(), any(), any()) } returns "TestMessage"

        // When/Then - exception is thrown (handled by CommandHandlerService)
        assertThrows<IllegalStateException> {
            skipCommand.onExecute(interaction, response)
        }

        // Then - skip should not be called because exception is thrown before
        coVerify(exactly = 0) {
            guildLavaPlayerService.skip()
        }
    }
}
