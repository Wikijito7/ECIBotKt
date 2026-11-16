package es.wokis.jda.player

import es.wokis.commands.neutral.CommandContext
import es.wokis.commands.neutral.CommandDefinition
import es.wokis.commands.neutral.CommandResponder
import es.wokis.commands.neutral.CommandResponse
import es.wokis.commands.neutral.GuildCommandRegistrar
import es.wokis.domain.locale.LocaleCode
import es.wokis.domain.model.ChannelId
import es.wokis.domain.model.GuildId
import es.wokis.domain.model.UserId
import es.wokis.jda.JdaRuntimeConfig
import es.wokis.jda.registerPlayerCommands
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class JdaPlayerCommandsTest {

    @Test
    fun `Given the player commands When registered Then publish both admin commands only in the configured guild`() = runTest {
        // Given
        val controller = newController()
        val registrar = RecordingGuildCommandRegistrar()
        val config = JdaRuntimeConfig(token = "fake-token", guildId = GuildId(987654321uL))
        val play = JdaPlayCommand(controller)
        val control = JdaPlayerControlCommand(controller)

        // When
        registerPlayerCommands(registrar, config, play, control)

        // Then
        assertEquals(2, registrar.registrations.size)
        assertTrue(registrar.registrations.all { it.guildId == config.guildId })
        assertEquals(
            mapOf(
                JdaPlayCommand.COMMAND_NAME to JdaPlayCommand.COMMAND_DESCRIPTION,
                JdaPlayerControlCommand.COMMAND_NAME to JdaPlayerControlCommand.COMMAND_DESCRIPTION,
            ),
            registrar.registrations.associate { it.command.name to it.command.description },
        )
        assertTrue(registrar.registrations.all { it.command.adminOnly })
    }

    @Test
    fun `Given the player definitions When inspected Then they are admin only guild commands with required string options`() {
        // Given
        val controller = newController()

        // When
        val play = JdaPlayCommand(controller).definition
        val control = JdaPlayerControlCommand(controller).definition

        // Then
        assertEquals(JdaPlayCommand.COMMAND_NAME, play.name)
        assertEquals(JdaPlayerControlCommand.COMMAND_NAME, control.name)
        assertTrue(play.adminOnly)
        assertTrue(control.adminOnly)
        assertFalse(play.description.isBlank())
        assertFalse(control.description.isBlank())

        val playOption = requireNotNull(play.stringOption)
        assertEquals(JdaPlayCommand.OPTION_INPUT, playOption.name)
        assertTrue(playOption.required)
        assertFalse(playOption.autocomplete)

        val controlOption = requireNotNull(control.stringOption)
        assertEquals(JdaPlayerControlCommand.OPTION_ACTION, controlOption.name)
        assertTrue(controlOption.required)
        assertFalse(controlOption.autocomplete)
    }

    @Test
    fun `Given a jda-play invocation When handled Then defer before loading and reply with the controller result`() = runTest {
        // Given
        val controller = newController()
        val order = mutableListOf<String>()
        val context = context(JdaPlayCommand.COMMAND_NAME, mapOf(JdaPlayCommand.OPTION_INPUT to "track"))
        val responder = mockk<CommandResponder>()
        val response = slot<CommandResponse>()
        coEvery { responder.defer(ephemeral = true) } coAnswers { order += "defer" }
        coEvery { controller.play(any(), "track") } coAnswers {
            order += "load"
            "loaded"
        }
        coEvery { responder.respond(capture(response)) } coAnswers { order += "respond" }

        // When
        JdaPlayCommand(controller).handle(context, responder)

        // Then
        assertEquals(listOf("defer", "load", "respond"), order)
        assertEquals(CommandResponse(content = "loaded", ephemeral = true), response.captured)
        coVerify(exactly = 1) {
            controller.play(
                match { it.commandName == JdaPlayCommand.COMMAND_NAME },
                "track",
            )
        }
    }

    @Test
    fun `Given a jda-player invocation When handled Then defer before controlling and reply with the controller result`() = runTest {
        // Given
        val controller = newController()
        val order = mutableListOf<String>()
        val context = context(
            JdaPlayerControlCommand.COMMAND_NAME,
            mapOf(JdaPlayerControlCommand.OPTION_ACTION to "pause"),
        )
        val responder = mockk<CommandResponder>()
        val response = slot<CommandResponse>()
        coEvery { responder.defer(ephemeral = true) } coAnswers { order += "defer" }
        coEvery { controller.control(any(), "pause") } coAnswers {
            order += "control"
            "paused"
        }
        coEvery { responder.respond(capture(response)) } coAnswers { order += "respond" }

        // When
        JdaPlayerControlCommand(controller).handle(context, responder)

        // Then
        assertEquals(listOf("defer", "control", "respond"), order)
        assertEquals(CommandResponse(content = "paused", ephemeral = true), response.captured)
        coVerify(exactly = 1) {
            controller.control(
                match { it.commandName == JdaPlayerControlCommand.COMMAND_NAME },
                "pause",
            )
        }
    }

    @Test
    fun `Given a missing option When the command is handled Then defer before playing and pass an empty string`() = runTest {
        // Given
        val controller = newController()
        val order = mutableListOf<String>()
        val context = context(JdaPlayCommand.COMMAND_NAME, emptyMap())
        val responder = mockk<CommandResponder>()
        coEvery { responder.defer(ephemeral = true) } coAnswers { order += "defer" }
        coEvery { controller.play(any(), "") } coAnswers {
            order += "play"
            "provide input"
        }
        coEvery { responder.respond(CommandResponse(content = "provide input", ephemeral = true)) } coAnswers {
            order += "respond"
        }

        // When
        JdaPlayCommand(controller).handle(context, responder)

        // Then: the interaction is acknowledged before the provider work starts and before the reply.
        assertEquals(listOf("defer", "play", "respond"), order)
        coVerify(exactly = 1) { responder.defer(ephemeral = true) }
        coVerify(exactly = 1) { controller.play(any(), "") }
        coVerify(exactly = 1) {
            responder.respond(CommandResponse(content = "provide input", ephemeral = true))
        }
    }

    /** Fresh strict controller per test so no stub or recorded call leaks across cases. */
    private fun newController(): JdaPlayerCommandController = mockk()

    private fun context(commandName: String, options: Map<String, String>): CommandContext = CommandContext(
        commandName = commandName,
        guildId = GuildId(42uL),
        channelId = ChannelId(43uL),
        userId = UserId(44uL),
        locale = LocaleCode("en-US"),
        stringOptions = options,
    )

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
