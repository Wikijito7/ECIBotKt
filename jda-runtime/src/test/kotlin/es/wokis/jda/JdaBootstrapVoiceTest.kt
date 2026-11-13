package es.wokis.jda

import es.wokis.commands.neutral.CommandContext
import es.wokis.commands.neutral.CommandDefinition
import es.wokis.commands.neutral.GuildCommandRegistrar
import es.wokis.commands.voicewalkthrough.JdaVoiceWalkthroughCommand
import es.wokis.commands.walkthrough.WalkthroughCommand
import es.wokis.domain.model.GuildId
import es.wokis.voice.AudioFrameSource
import es.wokis.voice.VoicePlaybackResult
import es.wokis.voice.VoiceSessionPort
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class JdaBootstrapVoiceTest {

    private val config = JdaRuntimeConfig(
        token = "fake-token",
        guildId = GuildId(987654321uL),
    )

    @Test
    fun `Given a validated config When the voice walkthrough registers Then use exactly the configured guild`() =
        runTest {
            // Given
            val registrar = RecordingGuildCommandRegistrar()
            val command = voiceCommand()

            // When
            registerVoiceWalkthroughCommand(registrar, config, command)

            // Then
            assertEquals(1, registrar.registrations.size)
            assertEquals(config.guildId, registrar.registrations.single().guildId)
            assertEquals(command.definition, registrar.registrations.single().command)
            assertEquals(JdaVoiceWalkthroughCommand.COMMAND_NAME, registrar.registrations.single().command.name)
        }

    @Test
    fun `Given both diagnostics When registered Then each is published only into the configured guild`() = runTest {
        // Given
        val registrar = RecordingGuildCommandRegistrar()
        val walkthrough = WalkthroughCommand()
        val voiceWalkthrough = voiceCommand()

        // When
        registerWalkthroughCommand(registrar, config, walkthrough)
        registerVoiceWalkthroughCommand(registrar, config, voiceWalkthrough)

        // Then
        assertEquals(2, registrar.registrations.size)
        assertTrue(registrar.registrations.all { it.guildId == config.guildId })
        assertEquals(
            setOf(WalkthroughCommand.COMMAND_NAME, JdaVoiceWalkthroughCommand.COMMAND_NAME),
            registrar.registrations.map { it.command.name }.toSet(),
        )
        assertEquals(
            mapOf(
                WalkthroughCommand.COMMAND_NAME to WalkthroughCommand.COMMAND_DESCRIPTION,
                JdaVoiceWalkthroughCommand.COMMAND_NAME to JdaVoiceWalkthroughCommand.COMMAND_DESCRIPTION,
            ),
            registrar.registrations.associate { it.command.name to it.command.description },
        )
    }

    @Test
    fun `Given the guild registrar port When inspected Then it exposes no global registration path`() {
        // When
        val methods = GuildCommandRegistrar::class.java.declaredMethods

        // Then: the value class mangles the JVM name, but there is exactly one registration
        // entry point and it always takes a guild destination.
        assertEquals(1, methods.size)
        assertTrue(
            methods.single().name.startsWith("register"),
            "unexpected registrar method name: ${methods.single().name}",
        )
        assertTrue(!methods.single().name.contains("global", ignoreCase = true))
    }

    private fun voiceCommand(): JdaVoiceWalkthroughCommand = JdaVoiceWalkthroughCommand(
        voiceSession = UnusedVoiceSession,
        frameSourceFactory = { throw IllegalStateException("not used during registration tests") },
    )

    private object UnusedVoiceSession : VoiceSessionPort {
        override suspend fun play(context: CommandContext, source: AudioFrameSource): VoicePlaybackResult =
            error("the voice session must not run during registration tests")
    }

    private data class Registration(val guildId: GuildId, val command: CommandDefinition)

    private class RecordingGuildCommandRegistrar : GuildCommandRegistrar {
        val registrations = mutableListOf<Registration>()

        override suspend fun register(guildId: GuildId, command: CommandDefinition) {
            registrations += Registration(guildId, command)
        }
    }
}
