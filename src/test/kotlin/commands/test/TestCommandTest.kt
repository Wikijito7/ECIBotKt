package commands.test

import dev.kord.core.entity.interaction.ChatInputCommandInteraction
import dev.kord.rest.builder.interaction.GlobalMultiApplicationCommandBuilder
import dev.kord.rest.builder.interaction.string
import es.wokis.commands.CommandsEnum
import es.wokis.commands.test.TestCommand
import es.wokis.services.lang.LanguageLocalizationService
import es.wokis.services.lavaplayer.GuildLavaPlayerService
import es.wokis.services.queue.GuildQueueService
import es.wokis.utils.getMemberVoiceChannel
import io.mockk.*
import kotlinx.coroutines.test.runTest
import mock.*
import org.junit.jupiter.api.Test
import kotlin.test.Ignore

class TestCommandTest {

    private val guildQueueService: GuildQueueService = mockk()
    private val languageLocalizationService: LanguageLocalizationService = mockk()

    private val testCommand = TestCommand(
        guildQueueService = guildQueueService,
        languageLocalizationService = languageLocalizationService
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
        testCommand.onRegisterCommand(commandBuilder)

        // Then
        verify(exactly = 1) {
            commandBuilder.input(name = CommandsEnum.TEST.commandName, description = "test test") {
                string(name = "pepe", description = "popopo") {
                    required = true
                }
            }
        }
    }

    @Test
    fun `Given command When onExecute is called Then execute command`() {
        runTest {
            // Given
            val mockedStrings: Map<String, String> = mapOf("pepe" to "asd")
            val interaction: ChatInputCommandInteraction = mockk {
                every { kord } returns mockedKord
                every { channel } returns mockedTextChannel
                every { command } returns mockk {
                    every { strings } returns mockedStrings
                }
            }
            val lavaPlayerService = mockk<GuildLavaPlayerService> {
                justRun { loadAndPlay(any()) }
            }

            coEvery {
                interaction.getMemberVoiceChannel(mockedKord)
            } returns mockedVoiceChannel

            every {
                guildQueueService.getOrCreateLavaPlayerService(
                    guildId = any(),
                    textChannel = any(),
                    voiceChannel = mockedVoiceChannel
                )
            } returns lavaPlayerService

            // When
            testCommand.onExecute(interaction, mockedResponse)

            // Then
            verify(exactly = 1) {
                guildQueueService.getOrCreateLavaPlayerService(
                    guildId = any(),
                    textChannel = any(),
                    voiceChannel = mockedVoiceChannel
                )
                lavaPlayerService.loadAndPlay("asd")
            }
        }
    }
}
