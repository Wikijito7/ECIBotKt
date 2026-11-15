package commands.shuffle

import commands.discordMessage
import dev.kord.common.Locale
import dev.kord.common.entity.Snowflake
import dev.kord.common.entity.optional.OptionalSnowflake
import dev.kord.core.Kord
import dev.kord.core.behavior.interaction.response.DeferredPublicMessageInteractionResponseBehavior
import dev.kord.core.entity.interaction.ChatInputCommandInteraction
import dev.kord.core.supplier.EntitySupplyStrategy
import es.wokis.commands.shuffle.ShuffleCommand
import es.wokis.services.lavaplayer.GuildLavaPlayerService
import es.wokis.services.localization.LocalizationService
import es.wokis.services.queue.GuildQueueService
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ShuffleCommandTest {
    private val guildQueueService: GuildQueueService = mockk()
    private val localizationService: LocalizationService = mockk()

    private val shuffleCommand = ShuffleCommand(
        guildQueueService = guildQueueService,
        localizationService = localizationService
    )

    @Test
    fun `Given interaction When onExecute is called Then execute shuffle command`() = runTest {
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
            coJustRun { shuffle() }
        }

        coEvery { guildQueueService.getOrCreateLavaPlayerService(any()) } returns guildLavaPlayerService
        coEvery { localizationService.getString(any(), any(), any()) } returns "TestMessage"

        // When
        shuffleCommand.onExecute(interaction, response)

        // Then
        coVerify(exactly = 1) {
            guildLavaPlayerService.shuffle()
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
            coJustRun { shuffle() }
        }
        coEvery { guildQueueService.getOrCreateLavaPlayerService(any()) } throws IllegalStateException()
        coEvery { localizationService.getString(any(), any(), any()) } returns "TestMessage"

        // When/Then
        assertThrows<IllegalStateException> {
            shuffleCommand.onExecute(interaction, response)
        }

        coVerify(exactly = 0) {
            guildLavaPlayerService.shuffle()
        }
    }
}
