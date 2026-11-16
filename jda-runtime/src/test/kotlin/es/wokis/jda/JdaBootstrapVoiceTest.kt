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

    private val guildId = GuildId(987654321uL)

    @Test
    fun `Given an explicit guild When the voice walkthrough registers Then use exactly that guild`() = runTest {
        val registrar = RecordingGuildCommandRegistrar()
        val command = voiceCommand()

        registerVoiceWalkthroughCommand(registrar, guildId, command)

        assertEquals(1, registrar.registrations.size)
        assertEquals(guildId, registrar.registrations.single().guildId)
        assertEquals(command.definition, registrar.registrations.single().command)
        assertEquals(JdaVoiceWalkthroughCommand.COMMAND_NAME, registrar.registrations.single().command.name)
    }

    @Test
    fun `Given both diagnostics When registered Then each is published only into the supplied guild`() = runTest {
        val registrar = RecordingGuildCommandRegistrar()
        val walkthrough = WalkthroughCommand()
        val voiceWalkthrough = voiceCommand()

        registerWalkthroughCommand(registrar, guildId, walkthrough)
        registerVoiceWalkthroughCommand(registrar, guildId, voiceWalkthrough)

        assertEquals(2, registrar.registrations.size)
        assertTrue(registrar.registrations.all { it.guildId == guildId })
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
    fun `Given two joined guilds When the diagnostics register Then every guild receives both commands`() = runTest {
        val registrar = RecordingGuildCommandRegistrar()
        val firstGuild = GuildId(111uL)
        val secondGuild = GuildId(222uL)
        val walkthrough = WalkthroughCommand()
        val voiceWalkthrough = voiceCommand()

        listOf(firstGuild, secondGuild).forEach { target ->
            registerWalkthroughCommand(registrar, target, walkthrough)
            registerVoiceWalkthroughCommand(registrar, target, voiceWalkthrough)
        }

        assertEquals(
            listOf(firstGuild, firstGuild, secondGuild, secondGuild),
            registrar.registrations.map { it.guildId },
        )
        assertEquals(
            listOf(
                WalkthroughCommand.COMMAND_NAME,
                JdaVoiceWalkthroughCommand.COMMAND_NAME,
                WalkthroughCommand.COMMAND_NAME,
                JdaVoiceWalkthroughCommand.COMMAND_NAME,
            ),
            registrar.registrations.map { it.command.name },
        )
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
