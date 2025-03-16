package commands.sounds

import dev.kord.common.Locale
import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.core.behavior.edit
import dev.kord.core.behavior.interaction.response.DeferredPublicMessageInteractionResponseBehavior
import dev.kord.core.entity.interaction.ButtonInteraction
import dev.kord.core.entity.interaction.ChatInputCommandInteraction
import dev.kord.core.supplier.EntitySupplyStrategy
import es.wokis.commands.ComponentsEnum
import es.wokis.commands.commons.createPaginatedEmbedMessage
import es.wokis.commands.sounds.SoundsCommand
import es.wokis.services.localization.LocalizationService
import es.wokis.utils.getFolderContent
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

class SoundsCommandTest {

    private val localizationService: LocalizationService = mockk()

    private val soundsCommand = SoundsCommand(
        localizationService = localizationService
    )

    @BeforeEach
    fun setUp() {
        mockkStatic(::getFolderContent)
        val file = mockk<File> {
            every { name } returns "manolete.mp3"
        }
        every { getFolderContent(any<String>()) } returns listOf(file)
    }

    @Test
    fun `Given interaction When onExecute Then respond with sounds message`() = runTest {
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

        every { localizationService.getString(any(), any()) } returns "TestMessage"
        every { localizationService.getStringFormat(any(), any(), *anyVararg()) } returns "Format"

        // When
        soundsCommand.onExecute(interaction, response)

        // Then
        verify(exactly = 1) {
            getFolderContent("./audio")
        }
    }

    @Test
    fun `Given sound previous interaction When onInteract Then respond with sounds message`() {
        val locale = Locale.BULGARIAN
        runTest {
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
                every { guildLocale } returns locale
                every { data } returns mockk {
                    every { guildId.value } returns Snowflake(123)
                }
                every { kord } returns mockKord
                every { component } returns mockk {
                    every { customId } returns ComponentsEnum.SOUNDS_PREVIOUS.customId
                }
                every { message } returns mockk(relaxed = true)
            }

            every { localizationService.getString(any(), any()) } returns "TestMessage"
            every { localizationService.getStringFormat(any(), any(), *anyVararg()) } returns "Format"

            // When
            soundsCommand.onInteract(interaction)

            // Then
            verify(exactly = 1) {
                getFolderContent("./audio")
            }
        }
    }

    @Test
    fun `Given sound next interaction When onInteract Then respond with sounds message`() {
        val locale = Locale.BULGARIAN
        runTest {
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
                every { guildLocale } returns locale
                every { data } returns mockk {
                    every { guildId.value } returns Snowflake(123)
                }
                every { kord } returns mockKord
                every { component } returns mockk {
                    every { customId } returns ComponentsEnum.SOUNDS_NEXT.customId
                }
                every { message } returns mockk(relaxed = true)
            }

            every { localizationService.getString(any(), any()) } returns "TestMessage"
            every { localizationService.getStringFormat(any(), any(), *anyVararg()) } returns "Format"

            // When
            soundsCommand.onInteract(interaction)

            // Then
            verify(exactly = 1) {
                getFolderContent("./audio")
            }
        }
    }
}