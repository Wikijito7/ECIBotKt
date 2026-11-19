package es.wokis.jda.player

import es.wokis.commands.neutral.CommandContext
import es.wokis.commands.neutral.CommandDefinition
import es.wokis.commands.neutral.CommandResponder
import es.wokis.commands.neutral.CommandResponse
import es.wokis.commands.neutral.GuildCommandRegistrar
import es.wokis.commands.player.PlayerCommandDefinitions
import es.wokis.domain.locale.GetGuildLocaleUseCase
import es.wokis.domain.locale.LocaleCode
import es.wokis.domain.locale.LocaleRepository
import es.wokis.domain.model.ChannelId
import es.wokis.domain.model.GuildId
import es.wokis.domain.model.UserId
import es.wokis.jda.registerPlayerCommands
import es.wokis.localization.GuildLocalizationService
import es.wokis.localization.LocalizationCatalog
import es.wokis.localization.PlayerMessageKeys
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

    private val localization = GuildLocalizationService(
        getGuildLocaleUseCase = GetGuildLocaleUseCase(EmptyLocaleRepository()),
        catalog = LocalizationCatalog.load(),
    )

    @Test
    fun `Given an explicit guild When the public player commands register Then publish both only there`() = runTest {
        val controller = newController()
        val registrar = RecordingGuildCommandRegistrar()
        val guildId = GuildId(987654321uL)
        val play = JdaPlayCommand(controller, localization)
        val control = JdaPlayerControlCommand(controller, localization)

        registerPlayerCommands(registrar, guildId, play, control)

        assertEquals(2, registrar.registrations.size)
        assertTrue(registrar.registrations.all { it.guildId == guildId })
        assertEquals(
            mapOf(
                JdaPlayCommand.COMMAND_NAME to localization.getDefaultString(PlayerMessageKeys.PLAY_COMMAND_DESCRIPTION),
                JdaPlayerControlCommand.COMMAND_NAME to
                    localization.getDefaultString(PlayerMessageKeys.PLAYER_COMMAND_DESCRIPTION),
            ),
            registrar.registrations.associate { it.command.name to it.command.description },
        )
        assertEquals(setOf("play", "player"), registrar.registrations.map { it.command.name }.toSet())
        assertTrue(registrar.registrations.none { it.command.adminOnly }, "both commands must be public")
    }

    @Test
    fun `Given two joined guilds When the public player commands register Then each guild receives both`() = runTest {
        val controller = newController()
        val registrar = RecordingGuildCommandRegistrar()
        val firstGuild = GuildId(111uL)
        val secondGuild = GuildId(222uL)
        val play = JdaPlayCommand(controller, localization)
        val control = JdaPlayerControlCommand(controller, localization)

        listOf(firstGuild, secondGuild).forEach { target ->
            registerPlayerCommands(registrar, target, play, control)
        }

        assertEquals(
            listOf(firstGuild, firstGuild, secondGuild, secondGuild),
            registrar.registrations.map { it.guildId },
        )
        assertEquals(
            listOf(
                JdaPlayCommand.COMMAND_NAME,
                JdaPlayerControlCommand.COMMAND_NAME,
                JdaPlayCommand.COMMAND_NAME,
                JdaPlayerControlCommand.COMMAND_NAME,
            ),
            registrar.registrations.map { it.command.name },
        )
        assertTrue(registrar.registrations.none { it.command.adminOnly })
    }

    @Test
    fun `Given the player definitions When inspected Then they are public guild commands with a required input and an optional action`() {
        val play = PlayerCommandDefinitions.play(localization)
        val control = PlayerCommandDefinitions.player(localization)

        assertEquals("play", play.name)
        assertEquals("player", control.name)
        assertEquals(JdaPlayCommand.COMMAND_NAME, play.name)
        assertEquals(JdaPlayerControlCommand.COMMAND_NAME, control.name)
        assertFalse(play.adminOnly)
        assertFalse(control.adminOnly)
        assertFalse(play.description.isBlank())
        assertFalse(control.description.isBlank())
        assertEquals(
            setOf(LocaleCode("en-US"), LocaleCode("es-ES")),
            play.descriptionLocalizations.keys,
        )
        assertEquals(
            setOf(LocaleCode("en-US"), LocaleCode("es-ES")),
            control.descriptionLocalizations.keys,
        )

        val playOption = requireNotNull(play.stringOption)
        assertEquals(JdaPlayCommand.OPTION_INPUT, playOption.name)
        assertEquals("input", playOption.name)
        assertTrue(playOption.required)
        assertFalse(playOption.autocomplete)
        assertEquals(
            setOf(LocaleCode("en-US"), LocaleCode("es-ES")),
            playOption.descriptionLocalizations.keys,
        )

        val controlOption = requireNotNull(control.stringOption)
        assertEquals(JdaPlayerControlCommand.OPTION_ACTION, controlOption.name)
        assertEquals("action", controlOption.name)
        assertFalse(
            controlOption.required,
            "Kord parity: an omitted /player action shows the player view, so the option must stay optional",
        )
        assertFalse(controlOption.autocomplete)
        assertEquals(
            setOf(LocaleCode("en-US"), LocaleCode("es-ES")),
            controlOption.descriptionLocalizations.keys,
        )
    }

    @Test
    fun `Given a play invocation When handled Then defer publicly before loading and reply publicly`() = runTest {
        val controller = newController()
        val order = mutableListOf<String>()
        val context = context(JdaPlayCommand.COMMAND_NAME, mapOf(JdaPlayCommand.OPTION_INPUT to "track"))
        val responder = mockk<CommandResponder>()
        val response = slot<CommandResponse>()
        coEvery { responder.defer(ephemeral = false) } coAnswers { order += "defer" }
        coEvery { controller.play(any(), "track") } coAnswers {
            order += "load"
            "loaded"
        }
        coEvery { responder.respond(capture(response)) } coAnswers { order += "respond" }

        JdaPlayCommand(controller, localization).handle(context, responder)

        assertEquals(listOf("defer", "load", "respond"), order)
        assertEquals(CommandResponse(content = "loaded", ephemeral = false), response.captured)
        coVerify(exactly = 1) { responder.defer(ephemeral = false) }
        coVerify(exactly = 0) { responder.defer(ephemeral = true) }
        coVerify(exactly = 1) {
            controller.play(
                match { it.commandName == JdaPlayCommand.COMMAND_NAME },
                "track",
            )
        }
    }

    @Test
    fun `Given a player invocation When handled Then defer publicly before controlling and reply publicly`() = runTest {
        val controller = newController()
        val order = mutableListOf<String>()
        val context = context(
            JdaPlayerControlCommand.COMMAND_NAME,
            mapOf(JdaPlayerControlCommand.OPTION_ACTION to "pause"),
        )
        val responder = mockk<CommandResponder>()
        val response = slot<CommandResponse>()
        coEvery { responder.defer(ephemeral = false) } coAnswers { order += "defer" }
        coEvery { controller.control(any(), "pause") } coAnswers {
            order += "control"
            "paused"
        }
        coEvery { responder.respond(capture(response)) } coAnswers { order += "respond" }

        JdaPlayerControlCommand(controller, localization).handle(context, responder)

        assertEquals(listOf("defer", "control", "respond"), order)
        assertEquals(CommandResponse(content = "paused", ephemeral = false), response.captured)
        coVerify(exactly = 1) { responder.defer(ephemeral = false) }
        coVerify(exactly = 0) { responder.defer(ephemeral = true) }
        coVerify(exactly = 1) {
            controller.control(
                match { it.commandName == JdaPlayerControlCommand.COMMAND_NAME },
                "pause",
            )
        }
    }

    @Test
    fun `Given a missing action When the player command is handled Then defer publicly and pass an empty string`() =
        runTest {
            val controller = newController()
            val order = mutableListOf<String>()
            val context = context(JdaPlayerControlCommand.COMMAND_NAME, emptyMap())
            val responder = mockk<CommandResponder>()
            coEvery { responder.defer(ephemeral = false) } coAnswers { order += "defer" }
            coEvery { controller.control(any(), "") } coAnswers {
                order += "control"
                "player shown"
            }
            coEvery { responder.respond(CommandResponse(content = "player shown", ephemeral = false)) } coAnswers {
                order += "respond"
            }

            JdaPlayerControlCommand(controller, localization).handle(context, responder)

            assertEquals(listOf("defer", "control", "respond"), order)
            coVerify(exactly = 1) { responder.defer(ephemeral = false) }
            coVerify(exactly = 1) { controller.control(any(), "") }
            coVerify(exactly = 1) {
                responder.respond(CommandResponse(content = "player shown", ephemeral = false))
            }
        }

    @Test
    fun `Given a missing option When the play command is handled Then defer before playing and pass an empty string`() =
        runTest {
            val controller = newController()
            val order = mutableListOf<String>()
            val context = context(JdaPlayCommand.COMMAND_NAME, emptyMap())
            val responder = mockk<CommandResponder>()
            coEvery { responder.defer(ephemeral = false) } coAnswers { order += "defer" }
            coEvery { controller.play(any(), "") } coAnswers {
                order += "play"
                "provide input"
            }
            coEvery { responder.respond(CommandResponse(content = "provide input", ephemeral = false)) } coAnswers {
                order += "respond"
            }

            JdaPlayCommand(controller, localization).handle(context, responder)

            assertEquals(listOf("defer", "play", "respond"), order)
            coVerify(exactly = 1) { responder.defer(ephemeral = false) }
            coVerify(exactly = 1) { controller.play(any(), "") }
            coVerify(exactly = 1) {
                responder.respond(CommandResponse(content = "provide input", ephemeral = false))
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

    private class EmptyLocaleRepository : LocaleRepository {
        override suspend fun getGuildLocale(guildId: GuildId): LocaleCode? = null

        override suspend fun setGuildLocale(guildId: GuildId, locale: LocaleCode) =
            error("command definition tests must not persist")

        override suspend fun removeGuildLocale(guildId: GuildId) =
            error("command definition tests must not persist")

        override suspend fun getAllGuildLocales(): Map<GuildId, LocaleCode> = emptyMap()
    }
}
