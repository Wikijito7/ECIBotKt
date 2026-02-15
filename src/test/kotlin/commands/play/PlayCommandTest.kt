package commands.play

import dev.kord.common.Locale
import dev.kord.core.entity.interaction.ChatInputCommandInteraction
import dev.kord.rest.builder.interaction.GlobalMultiApplicationCommandBuilder
import dev.kord.rest.builder.interaction.string
import es.wokis.commands.CommandName
import es.wokis.services.lavaplayer.GuildLavaPlayerService
import es.wokis.services.localization.LocalizationService
import es.wokis.services.queue.GuildQueueService
import es.wokis.utils.getMemberVoiceChannel
import io.mockk.*
import kotlinx.coroutines.test.runTest
import mock.*
import org.junit.jupiter.api.Test
import kotlin.test.Ignore

class PlayCommandTest {

    private val guildQueueService: GuildQueueService = mockk()
    private val localizationService: LocalizationService = mockk()

    private val playCommand = PlayCommand(
        guildQueueService = guildQueueService,
        localizationService = localizationService
    )

    /*
        Verification failed: call 2 of 2: List(child of #2#3).add(any())) was not called
        java.lang.AssertionError: Verification failed: call 2 of 2: List(child of #2#3).add(any())) was not called
            at io.mockk.impl.recording.states.VerifyingState.failIfNotPassed(VerifyingState.kt:63)
            at io.mockk.impl.recording.states.VerifyingState.recordingDone(VerifyingState.kt:42)
            at io.mockk.impl.recording.CommonCallRecorder.done(CommonCallRecorder.kt:47)
            at io.mockk.impl.eval.RecordedBlockEvaluator.record(RecordedBlockEvaluator.kt:63)
            at io.mockk.impl.eval.VerifyBlockEvaluator.verify(VerifyBlockEvaluator.kt:30)
            at io.mockk.MockKDsl.internalVerify(API.kt:120)
            at io.mockk.MockKKt.verify(MockK.kt:218)
            at io.mockk.MockKKt.verify$default(MockK.kt:209)
            at commands.test.TestCommandTest.Given command When onRegisterCommand is called Then register test command(TestCommandTest.kt:32)
            at java.base/java.lang.reflect.Method.invoke(Method.java:569)
            at java.base/java.util.ArrayList.forEach(ArrayList.java:1511)
            at java.base/java.util.ArrayList.forEach(ArrayList.java:1511)
     */
    @Test
    @Ignore("Mockk fails")
    fun `Given command When onRegisterCommand is called Then register test command`() {
        // Given
        val commandBuilder: GlobalMultiApplicationCommandBuilder = mockk {
            every { commands } returns mockk {
                every { add(any()) } returns true
            }
        }

        // When
        playCommand.onRegisterCommand(commandBuilder)

        // Then
        verify(exactly = 1) {
            commandBuilder.input(name = CommandName.Play.commandName, description = "test test") {
                string(name = "pepe", description = "popopo") {
                    required = true
                }
            }
        }
    }

    @Test
    fun `Given command with non-url input When onExecute is called Then pass input directly to lavaplayer`() = runTest {
        val input = "some search query"
        val mockedStrings: Map<String, String> = mapOf("content" to input)
        val interaction: ChatInputCommandInteraction = mockk {
            every { kord } returns mockedKord
            every { channel } returns mockedTextChannel
            every { command } returns mockk {
                every { strings } returns mockedStrings
            }
            every { data } returns mockk {
                every { guildId.value } returns null
            }
        }
        val lavaPlayerService = mockk<GuildLavaPlayerService> {
            justRun { loadAndPlayMultiple(any()) }
        }

        coEvery {
            interaction.getMemberVoiceChannel(mockedKord)
        } returns mockedVoiceChannel

        coEvery {
            guildQueueService.getOrCreateLavaPlayerService(
                interaction = interaction
            )
        } returns lavaPlayerService
        every { interaction.guildLocale } returns Locale.ENGLISH_UNITED_STATES
        coEvery { localizationService.getString(any(), any(), any()) } returns ""
        coEvery { localizationService.getStringFormat(any(), any(), any(), *anyVararg()) } returns ""

        playCommand.onExecute(interaction, mockedResponse)

        coVerify(exactly = 1) {
            guildQueueService.getOrCreateLavaPlayerService(
                interaction = interaction
            )
            lavaPlayerService.loadAndPlayMultiple(listOf("some", "search", "query"))
        }
    }

    @Test
    fun `Given command with url When onExecute is called Then play url`() = runTest {
        // Given
        val url = "https://something.like/this"
        val mockedStrings: Map<String, String> = mapOf("content" to url)
        val interaction: ChatInputCommandInteraction = mockk {
            every { kord } returns mockedKord
            every { channel } returns mockedTextChannel
            every { command } returns mockk {
                every { strings } returns mockedStrings
            }
            every { data } returns mockk {
                every { guildId.value } returns null
            }
        }
        val lavaPlayerService = mockk<GuildLavaPlayerService> {
            justRun { loadAndPlayMultiple(any()) }
        }

        coEvery {
            interaction.getMemberVoiceChannel(mockedKord)
        } returns mockedVoiceChannel

        coEvery {
            guildQueueService.getOrCreateLavaPlayerService(
                interaction = interaction
            )
        } returns lavaPlayerService
        every { interaction.guildLocale } returns Locale.ENGLISH_UNITED_STATES
        coEvery { localizationService.getString(any(), any(), any()) } returns ""
        coEvery { localizationService.getStringFormat(any(), any(), any(), *anyVararg()) } returns ""

        // When
        playCommand.onExecute(interaction, mockedResponse)

        // Then
        coVerify(exactly = 1) {
            guildQueueService.getOrCreateLavaPlayerService(
                interaction = interaction
            )
            lavaPlayerService.loadAndPlayMultiple(listOf(url))
        }
    }

    @Test
    fun `Given command with empty url When onExecute is called Then play url`() = runTest {
        // Given
        val url = ""
        val mockedStrings: Map<String, String> = mapOf("content" to url)
        val interaction: ChatInputCommandInteraction = mockk {
            every { kord } returns mockedKord
            every { channel } returns mockedTextChannel
            every { command } returns mockk {
                every { strings } returns mockedStrings
            }
            every { data } returns mockk {
                every { guildId.value } returns null
            }
        }
        val lavaPlayerService = mockk<GuildLavaPlayerService> {
            justRun { loadAndPlayMultiple(any()) }
        }

        coEvery {
            interaction.getMemberVoiceChannel(mockedKord)
        } returns mockedVoiceChannel

        coEvery {
            guildQueueService.getOrCreateLavaPlayerService(
                interaction = interaction
            )
        } returns lavaPlayerService
        every { interaction.guildLocale } returns Locale.ENGLISH_UNITED_STATES
        coEvery { localizationService.getString(any(), any(), any()) } returns ""
        coEvery { localizationService.getStringFormat(any(), any(), any(), *anyVararg()) } returns ""

        // When
        playCommand.onExecute(interaction, mockedResponse)

        // Then
        coVerify(exactly = 0) {
            guildQueueService.getOrCreateLavaPlayerService(
                interaction = interaction
            )
            lavaPlayerService.loadAndPlayMultiple(any())
        }
    }

    @Test
    fun `Given command without url When onExecute is called Then play url`() = runTest {
        // Given
        val mockedStrings: Map<String, String> = emptyMap()
        val interaction: ChatInputCommandInteraction = mockk {
            every { kord } returns mockedKord
            every { channel } returns mockedTextChannel
            every { command } returns mockk {
                every { strings } returns mockedStrings
            }
            every { data } returns mockk {
                every { guildId.value } returns null
            }
        }
        val lavaPlayerService = mockk<GuildLavaPlayerService> {
            justRun { loadAndPlayMultiple(any()) }
        }

        coEvery {
            interaction.getMemberVoiceChannel(mockedKord)
        } returns mockedVoiceChannel

        coEvery {
            guildQueueService.getOrCreateLavaPlayerService(
                interaction = interaction
            )
        } returns lavaPlayerService
        every { interaction.guildLocale } returns Locale.ENGLISH_UNITED_STATES
        coEvery { localizationService.getString(any(), any(), any()) } returns ""
        coEvery { localizationService.getStringFormat(any(), any(), any(), *anyVararg()) } returns ""

        // When
        playCommand.onExecute(interaction, mockedResponse)

        // Then
        coVerify(exactly = 0) {
            guildQueueService.getOrCreateLavaPlayerService(
                interaction = interaction
            )
            lavaPlayerService.loadAndPlayMultiple(any())
        }
    }
}
