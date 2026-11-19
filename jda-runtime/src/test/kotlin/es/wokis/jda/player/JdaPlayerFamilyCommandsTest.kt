package es.wokis.jda.player

import es.wokis.commands.disconnect.DisconnectCommandHandler
import es.wokis.commands.neutral.CommandContext
import es.wokis.commands.neutral.CommandDefinition
import es.wokis.commands.neutral.CommandHandler
import es.wokis.commands.neutral.CommandResponder
import es.wokis.commands.neutral.GuildCommandRegistrar
import es.wokis.commands.next.NextCommandHandler
import es.wokis.commands.queue.QueueCommandHandler
import es.wokis.commands.reconnect.ReconnectCommandHandler
import es.wokis.commands.shuffle.ShuffleCommandHandler
import es.wokis.commands.skip.SkipCommandHandler
import es.wokis.domain.locale.GetGuildLocaleUseCase
import es.wokis.domain.locale.LocaleCode
import es.wokis.domain.locale.LocaleRepository
import es.wokis.domain.model.ChannelId
import es.wokis.domain.model.GuildId
import es.wokis.domain.model.UserId
import es.wokis.localization.GuildLocalizationService
import es.wokis.localization.LocalizationCatalog
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class JdaPlayerFamilyCommandsTest {

    private val localization: GuildLocalizationService = GuildLocalizationService(
        getGuildLocaleUseCase = GetGuildLocaleUseCase(EmptyLocaleRepository()),
        catalog = LocalizationCatalog.load(),
    )
    private val registrar = RecordingRegistrar()

    @Test
    fun `Given each family command When registered Then publish its Kord definition in the guild`() = runTest {
        val guildId = GuildId(7uL)

        familyCommands().forEach { family -> family.adapter.registerIn(registrar, guildId) }

        assertEquals(
            listOf("skip", "shuffle", "next", "queue", "disconnect", "reconnect"),
            registrar.registrations.map { it.command.name },
        )
        assertTrue(registrar.registrations.all { it.guildId == guildId })
        assertTrue(registrar.registrations.none { it.command.adminOnly }, "every player command must stay public")
        assertEquals(
            "track",
            registrar.registrations.first { it.command.name == "next" }.command.stringOption?.name,
        )
        assertTrue(registrar.registrations.filter { it.command.name != "next" }.all { it.command.stringOption == null })
    }

    @Test
    fun `Given each family command When handled Then defer publicly before the neutral handler runs`() = runTest {
        familyCommands().forEach { family ->
            val responder: CommandResponder = mockk()
            coEvery { responder.defer(ephemeral = false) } coAnswers { family.events += "defer" }

            family.adapter.handle(context(), responder)

            assertEquals(listOf("defer", "delegate"), family.events, family.expectedName)
            coVerify(exactly = 1) { responder.defer(ephemeral = false) }
            coVerify(exactly = 0) { responder.defer(ephemeral = true) }
        }
    }

    @Test
    fun `Given each family command When inspected Then expose the delegate command name`() {
        familyCommands().forEach { family ->
            assertEquals(family.expectedName, family.adapter.name)
        }
    }

    private inline fun <reified T : CommandHandler> family(
        name: String,
        crossinline wrap: (T) -> JdaNeutralPlayerCommand,
    ): Family {
        val events = mutableListOf<String>()
        val handler: T = mockk()
        every { handler.name } returns name
        coEvery { handler.handle(any(), any()) } coAnswers { events += "delegate" }
        return Family(wrap(handler), name, events)
    }

    private fun familyCommands(): List<Family> = listOf(
        family<SkipCommandHandler>("skip") { JdaSkipCommand(it, localization) },
        family<ShuffleCommandHandler>("shuffle") { JdaShuffleCommand(it, localization) },
        family<NextCommandHandler>("next") { JdaNextCommand(it, localization) },
        family<QueueCommandHandler>("queue") { JdaQueueCommand(it, localization) },
        family<DisconnectCommandHandler>("disconnect") { JdaDisconnectCommand(it, localization) },
        family<ReconnectCommandHandler>("reconnect") { JdaReconnectCommand(it, localization) },
    )

    private fun context(): CommandContext = CommandContext(
        commandName = "family",
        guildId = GuildId(42uL),
        channelId = ChannelId(43uL),
        userId = UserId(44uL),
        locale = null,
    )

    private data class Family(
        val adapter: JdaNeutralPlayerCommand,
        val expectedName: String,
        val events: MutableList<String>,
    )

    private class RecordingRegistrar : GuildCommandRegistrar {
        val registrations = mutableListOf<Registration>()

        override suspend fun register(guildId: GuildId, command: CommandDefinition) {
            registrations += Registration(guildId, command)
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

    private data class Registration(val guildId: GuildId, val command: CommandDefinition)
}
