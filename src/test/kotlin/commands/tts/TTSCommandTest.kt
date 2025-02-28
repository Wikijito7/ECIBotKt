package commands.tts

import dev.kord.common.Locale
import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.core.behavior.interaction.response.DeferredPublicMessageInteractionResponseBehavior
import dev.kord.core.entity.interaction.ChatInputCommandInteraction
import dev.kord.core.supplier.EntitySupplyStrategy
import dev.kord.rest.builder.interaction.GlobalMultiApplicationCommandBuilder
import dev.kord.rest.builder.interaction.string
import es.wokis.commands.CommandsEnum
import es.wokis.commands.tts.TTSCommand
import es.wokis.localization.LocalizationKeys
import es.wokis.services.lavaplayer.GuildLavaPlayerService
import es.wokis.services.localization.LocalizationService
import es.wokis.services.queue.GuildQueueService
import es.wokis.services.tts.TTSService
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.Ignore
import kotlin.test.Test

private const val TTS_ARGUMENT_NAME = "message"

class TTSCommandTest {

    private val ttsService = mockk<TTSService>()
    private val localizationService = mockk<LocalizationService>()
    private val guildQueueService = mockk<GuildQueueService>()

    private val ttsCommand = TTSCommand(
        ttsService = ttsService,
        localizationService = localizationService,
        guildQueueService = guildQueueService
    )
    /*
        Verification failed: call 6 of 6: List(child of #4#6).add(eq(dev.kord.rest.builder.interaction.ChatInputCreateBuilderImpl@30cb489a))) was not called
        java.lang.AssertionError: Verification failed: call 6 of 6: List(child of #4#6).add(eq(dev.kord.rest.builder.interaction.ChatInputCreateBuilderImpl@30cb489a))) was not called
     */
    @Test
    @Ignore("Mockk fails")
    fun `Given tts command When onRegisterCommand is called Then command is registered`() {
        // Given
        val commandBuilder = mockk<GlobalMultiApplicationCommandBuilder>(relaxed = true)
        every { localizationService.getString(any()) } returns ""
        every { localizationService.getLocalizations(any()) } returns mutableMapOf()

        // When
        ttsCommand.onRegisterCommand(commandBuilder)

        // Then
        verify(exactly = 1) {
            commandBuilder.input(
                name = CommandsEnum.TTS.commandName,
                description = localizationService.getString(key = LocalizationKeys.TTS_COMMAND_DESCRIPTION)
            ) {
                descriptionLocalizations =
                    localizationService.getLocalizations(key = LocalizationKeys.TTS_COMMAND_DESCRIPTION)
                string(
                    name = TTS_ARGUMENT_NAME,
                    description = localizationService.getString(key = LocalizationKeys.TTS_COMMAND_INPUT_DESCRIPTION)
                ) {
                    descriptionLocalizations =
                        localizationService.getLocalizations(key = LocalizationKeys.TTS_COMMAND_INPUT_DESCRIPTION)
                    required = true
                }
            }
        }
    }

    @Test
    fun `Given interaction When onExecute is called Then execute tts command`() = runTest {
        // Given
        val mockedStrings: Map<String, String> = mapOf(TTS_ARGUMENT_NAME to "asd")
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
            every { command } returns mockk {
                every { strings } returns mockedStrings
            }
            every { kord } returns mockKord
        }
        val response = mockk<DeferredPublicMessageInteractionResponseBehavior> {
            every { kord } returns mockKord
            every { applicationId } returns Snowflake(456)
            every { token } returns "testToken"
        }
        val guildLavaPlayerService: GuildLavaPlayerService = mockk()

        coEvery { guildQueueService.getOrCreateLavaPlayerService(any()) } returns guildLavaPlayerService
        every { localizationService.getString(any(), any()) } returns "TestMessage"
        coJustRun { ttsService.loadAndPlayMessage(any(), any()) }

        // When
        ttsCommand.onExecute(interaction, response)

        // Then
        coVerify(exactly = 1) {
            ttsService.loadAndPlayMessage(guildLavaPlayerService, "asd")
        }
    }

    @Test
    fun `Given interaction with wrong argument When onExecute is called Then don't execute tts command`() = runTest {
        // Given
        val mockedStrings: Map<String, String> = mapOf("pepe" to "asd")
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
            every { command } returns mockk {
                every { strings } returns mockedStrings
            }
            every { kord } returns mockKord
        }
        val response = mockk<DeferredPublicMessageInteractionResponseBehavior> {
            every { kord } returns mockKord
            every { applicationId } returns Snowflake(456)
            every { token } returns "testToken"
        }
        val guildLavaPlayerService: GuildLavaPlayerService = mockk()

        coEvery { guildQueueService.getOrCreateLavaPlayerService(any()) } returns guildLavaPlayerService
        every { localizationService.getString(any(), any()) } returns "TestMessage"
        every { localizationService.getStringFormat(any(), any(), *anyVararg()) } returns ""
        coJustRun { ttsService.loadAndPlayMessage(any(), any()) }

        // When
        ttsCommand.onExecute(interaction, response)

        // Then
        coVerify(exactly = 0) {
            ttsService.loadAndPlayMessage(any(), any())
        }
    }

    @Test
    fun `Given interaction without voice channel When onExecute is called Then don't execute tts command`() = runTest {
        // Given
        val mockedStrings: Map<String, String> = mapOf("pepe" to "asd")
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
            every { command } returns mockk {
                every { strings } returns mockedStrings
            }
            every { kord } returns mockKord
        }
        val response = mockk<DeferredPublicMessageInteractionResponseBehavior> {
            every { kord } returns mockKord
            every { applicationId } returns Snowflake(456)
            every { token } returns "testToken"
        }

        coEvery { guildQueueService.getOrCreateLavaPlayerService(any()) } throws IllegalStateException()
        every { localizationService.getString(any(), any()) } returns "TestMessage"
        every { localizationService.getStringFormat(any(), any(), *anyVararg()) } returns ""
        coJustRun { ttsService.loadAndPlayMessage(any(), any()) }

        // When
        ttsCommand.onExecute(interaction, response)

        // Then
        coVerify(exactly = 0) {
            ttsService.loadAndPlayMessage(any(), any())
        }
    }
}
