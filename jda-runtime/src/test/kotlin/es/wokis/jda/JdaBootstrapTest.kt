package es.wokis.jda

import es.wokis.commands.neutral.CommandDefinition
import es.wokis.commands.neutral.GuildCommandRegistrar
import es.wokis.commands.walkthrough.WalkthroughCommand
import es.wokis.domain.model.GuildId
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class JdaBootstrapTest {

    @Test
    fun `Given a validated config When walkthrough is registered Then use exactly the configured guild`() = runTest {
        // Given
        val registrar = RecordingGuildCommandRegistrar()
        val config = JdaRuntimeConfig(
            token = "fake-token",
            guildId = GuildId(987654321uL),
        )
        val walkthrough = WalkthroughCommand()

        // When
        registerWalkthroughCommand(registrar, config, walkthrough)

        // Then
        assertEquals(1, registrar.registrations.size)
        assertEquals(config.guildId, registrar.registrations.single().guildId)
        assertEquals(walkthrough.definition, registrar.registrations.single().command)
    }

    @Test
    fun `Given the default walkthrough When registered Then pass the documented command definition`() = runTest {
        // Given
        val registrar = RecordingGuildCommandRegistrar()
        val config = JdaRuntimeConfig(
            token = "fake-token",
            guildId = GuildId(42uL),
        )

        // When
        registerWalkthroughCommand(registrar, config)

        // Then
        assertEquals(1, registrar.registrations.size)
        val registration = registrar.registrations.single()
        assertEquals(config.guildId, registration.guildId)
        assertEquals(WalkthroughCommand.COMMAND_NAME, registration.command.name)
        assertEquals(WalkthroughCommand.COMMAND_DESCRIPTION, registration.command.description)
        assertTrue(registration.command.description.isNotBlank())
    }

    @Test
    fun `Given the guild registrar port When walkthrough registers Then use no other destination`() = runTest {
        // Given
        val registrar = RecordingGuildCommandRegistrar()
        val firstGuild = GuildId(111uL)
        val secondGuild = GuildId(222uL)

        // When
        registerWalkthroughCommand(registrar, JdaRuntimeConfig(token = "fake-token", guildId = firstGuild))
        registerWalkthroughCommand(registrar, JdaRuntimeConfig(token = "fake-token", guildId = secondGuild))

        // Then
        assertEquals(listOf(firstGuild, secondGuild), registrar.registrations.map { it.guildId })
        assertEquals(
            listOf(WalkthroughCommand().definition, WalkthroughCommand().definition),
            registrar.registrations.map { it.command },
        )
    }

    private data class Registration(
        val guildId: GuildId,
        val command: CommandDefinition,
    )

    private class RecordingGuildCommandRegistrar : GuildCommandRegistrar {
        val registrations = mutableListOf<Registration>()

        override suspend fun register(guildId: GuildId, command: CommandDefinition) {
            registrations += Registration(guildId = guildId, command = command)
        }
    }
}
