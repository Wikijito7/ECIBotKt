package es.wokis.jda.command

import es.wokis.commands.locale.LocaleCommandHandler
import es.wokis.commands.neutral.CommandDefinition
import es.wokis.commands.player.PlayerCommandDefinitions
import es.wokis.domain.locale.GetGuildLocaleUseCase
import es.wokis.domain.locale.LocaleCode
import es.wokis.domain.locale.LocaleRepository
import es.wokis.domain.model.GuildId
import es.wokis.localization.GuildLocalizationService
import es.wokis.localization.LocalizationCatalog
import es.wokis.localization.PlayerMessageKeys
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.exceptions.ErrorResponseException
import net.dv8tion.jda.api.interactions.DiscordLocale
import net.dv8tion.jda.api.interactions.commands.Command
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.CommandData
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import net.dv8tion.jda.api.requests.ErrorResponse
import net.dv8tion.jda.api.requests.Response
import net.dv8tion.jda.api.requests.RestAction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.function.Consumer

class JdaGuildCommandRegistrarTest {

    private val jda: JDA = mockk()
    private val guild: Guild = mockk()
    private val localization = GuildLocalizationService(
        getGuildLocaleUseCase = GetGuildLocaleUseCase(EmptyLocaleRepository()),
        catalog = LocalizationCatalog.load(),
    )

    @Test
    fun `Given a member guild When registering Then publish the command only in that guild`() = runTest {
        val guildId = GuildId(1234567890123456789uL)
        val jdaGuildId = 1234567890123456789L
        val command = CommandDefinition(
            name = "walkthrough",
            description = "Start the guided server walkthrough",
        )
        val published = slot<SlashCommandData>()
        val upsertAction = successfulUpsertAction()
        every { jda.getGuildById(jdaGuildId) } returns guild
        every { guild.upsertCommand(capture(published)) } returns upsertAction

        JdaGuildCommandRegistrar(jda).register(guildId, command)

        verify(exactly = 1) { jda.getGuildById(jdaGuildId) }
        verify(exactly = 1) { guild.upsertCommand(any<CommandData>()) }
        verify(exactly = 1) { upsertAction.queue(any(), any()) }

        assertEquals(command.name, published.captured.name)
        assertEquals(command.description, published.captured.description)

        verify(exactly = 0) { jda.upsertCommand(any<CommandData>()) }
        verify(exactly = 0) { jda.upsertCommand(any<String>(), any<String>()) }
    }

    @Test
    fun `Given the guild upsert emits a REST failure When registering Then propagate it without an application-wide fallback`() = runTest {
        val guildId = GuildId(1234567890123456789uL)
        val jdaGuildId = 1234567890123456789L
        val command = CommandDefinition(
            name = "walkthrough",
            description = "Start the guided server walkthrough",
        )
        val expectedFailure = ErrorResponseException.create(
            ErrorResponse.MISSING_ACCESS,
            Response(
                IllegalStateException("Discord rejected the guild command upsert"),
                emptySet(),
            ),
        )
        val upsertAction: RestAction<Command> = mockk()
        every { jda.getGuildById(jdaGuildId) } returns guild
        every { guild.upsertCommand(any<CommandData>()) } returns upsertAction
        every { upsertAction.queue(any(), any()) } answers {
            secondArg<Consumer<Throwable>>().accept(expectedFailure)
        }

        val failure = runCatching {
            JdaGuildCommandRegistrar(jda).register(guildId, command)
        }.exceptionOrNull()

        assertSame(expectedFailure, failure)
        verify(exactly = 1) { jda.getGuildById(jdaGuildId) }
        verify(exactly = 1) { guild.upsertCommand(any<CommandData>()) }
        verify(exactly = 1) { upsertAction.queue(any(), any()) }

        verify(exactly = 0) { jda.upsertCommand(any<CommandData>()) }
        verify(exactly = 0) { jda.upsertCommand(any<String>(), any<String>()) }
    }

    @Test
    fun `Given a guild the bot is not in When registering Then fail without a global fallback`() = runTest {
        val guildId = GuildId(987654321uL)
        val jdaGuildId = 987654321L
        val command = CommandDefinition(
            name = "walkthrough",
            description = "Start the guided server walkthrough",
        )
        every { jda.getGuildById(jdaGuildId) } returns null

        val failure = runCatching {
            JdaGuildCommandRegistrar(jda).register(guildId, command)
        }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertEquals(
            "The bot is not a member of guild $guildId, so /${command.name} cannot be registered",
            failure?.message,
        )
        verify(exactly = 0) { guild.upsertCommand(any<CommandData>()) }
        verify(exactly = 0) { jda.upsertCommand(any<CommandData>()) }
        verify(exactly = 0) { jda.upsertCommand(any<String>(), any<String>()) }
    }

    @Test
    fun `Given a guild id outside JDA's signed range When registering Then reject before touching JDA`() = runTest {
        val outOfRangeGuildId = GuildId(Long.MAX_VALUE.toULong() + 1uL)
        val command = CommandDefinition(
            name = "walkthrough",
            description = "Start the guided server walkthrough",
        )

        val failure = runCatching {
            JdaGuildCommandRegistrar(jda).register(outOfRangeGuildId, command)
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertEquals(
            "Guild id does not fit in JDA's signed 64-bit id range",
            failure?.message,
        )
        assertFalse(
            failure?.message.orEmpty().contains(outOfRangeGuildId.value.toString()),
            "the failure message echoed the invalid guild id: ${failure?.message}",
        )
        verify(exactly = 0) { jda.getGuildById(any<Long>()) }
        verify(exactly = 0) { jda.getGuildById(any<String>()) }
        verify(exactly = 0) { jda.upsertCommand(any<CommandData>()) }
    }

    @Test
    fun `Given the locale definition When registering Then publish an admin guild command with a required autocomplete option`() =
        runTest {
            val guildId = GuildId(1234567890123456789uL)
            val jdaGuildId = 1234567890123456789L
            val command = LocaleCommandHandler.definition(
                description = "Set the preferred locale for this guild",
                optionDescription = "Locale to set (use autocomplete to see available options)",
                descriptionLocalizations = mapOf(
                    LocaleCode("en-US") to "Set the preferred locale for this guild",
                    LocaleCode("es-ES") to "Establecer el idioma preferido para este servidor",
                    LocaleCode("xx-YY") to "not a Discord locale",
                    LocaleCode("unknown") to "not a Discord locale",
                ),
                optionDescriptionLocalizations = mapOf(
                    LocaleCode("en-US") to "Locale to set (use autocomplete to see available options)",
                    LocaleCode("es-ES") to
                        "Idioma a establecer (usa el autocompletado para ver las opciones disponibles)",
                    LocaleCode("xx-YY") to "not a Discord locale",
                    LocaleCode("unknown") to "not a Discord locale",
                ),
            )
            val published = slot<SlashCommandData>()
            val upsertAction = successfulUpsertAction()
            every { jda.getGuildById(jdaGuildId) } returns guild
            every { guild.upsertCommand(capture(published)) } returns upsertAction

            JdaGuildCommandRegistrar(jda).register(guildId, command)

            verify(exactly = 1) { jda.getGuildById(jdaGuildId) }
            verify(exactly = 1) { guild.upsertCommand(any<CommandData>()) }
            verify(exactly = 0) { jda.upsertCommand(any<CommandData>()) }

            val data = published.captured
            assertEquals("locale", data.name)
            assertEquals("Set the preferred locale for this guild", data.description)
            assertEquals(
                DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR),
                data.defaultPermissions,
            )

            val option = data.options.single()
            assertEquals(OptionType.STRING, option.type)
            assertEquals("locale", option.name)
            assertEquals("Locale to set (use autocomplete to see available options)", option.description)
            assertTrue(option.isRequired)
            assertTrue(option.isAutoComplete)

            // Canonical neutral tags become JDA locales at the edge, and unknown tags are dropped
            assertEquals(
                mapOf(
                    DiscordLocale.ENGLISH_US to "Set the preferred locale for this guild",
                    DiscordLocale.SPANISH to "Establecer el idioma preferido para este servidor",
                ),
                data.descriptionLocalizations.toMap(),
            )
            assertEquals(
                mapOf(
                    DiscordLocale.ENGLISH_US to
                        "Locale to set (use autocomplete to see available options)",
                    DiscordLocale.SPANISH to
                        "Idioma a establecer (usa el autocompletado para ver las opciones disponibles)",
                ),
                option.descriptionLocalizations.toMap(),
            )
        }

    @Test
    fun `Given a public player definition When registering Then publish an enabled guild command with the optional action option`() =
        runTest {
            val guildId = GuildId(1234567890123456789uL)
            val jdaGuildId = 1234567890123456789L
            val command = PlayerCommandDefinitions.player(localization)
            val published = slot<SlashCommandData>()
            val upsertAction = successfulUpsertAction()
            every { jda.getGuildById(jdaGuildId) } returns guild
            every { guild.upsertCommand(capture(published)) } returns upsertAction

            JdaGuildCommandRegistrar(jda).register(guildId, command)

            verify(exactly = 1) { jda.getGuildById(jdaGuildId) }
            verify(exactly = 1) { guild.upsertCommand(any<CommandData>()) }
            verify(exactly = 0) { jda.upsertCommand(any<CommandData>()) }
            verify(exactly = 0) { jda.upsertCommand(any<String>(), any<String>()) }

            val data = published.captured
            assertEquals("player", data.name)
            assertEquals(PlayerCommandDefinitions.PLAYER_COMMAND_NAME, data.name)
            assertEquals(
                localization.getDefaultString(PlayerMessageKeys.PLAYER_COMMAND_DESCRIPTION),
                data.description,
            )
            assertEquals(DefaultMemberPermissions.ENABLED, data.defaultPermissions)

            val option = data.options.single()
            assertEquals(OptionType.STRING, option.type)
            assertEquals(PlayerCommandDefinitions.PLAYER_OPTION_ACTION, option.name)
            assertEquals(
                localization.getDefaultString(PlayerMessageKeys.PLAYER_COMMAND_ACTION_DESCRIPTION),
                option.description,
            )
            assertFalse(
                option.isRequired,
                "Kord parity: an omitted /player action shows the player view, so the option must stay optional",
            )
            assertFalse(option.isAutoComplete)
        }

    @Test
    fun `Given a public play definition When registering Then publish an enabled guild command with the required input option`() =
        runTest {
            val guildId = GuildId(1234567890123456789uL)
            val jdaGuildId = 1234567890123456789L
            val command = PlayerCommandDefinitions.play(localization)
            val published = slot<SlashCommandData>()
            val upsertAction = successfulUpsertAction()
            every { jda.getGuildById(jdaGuildId) } returns guild
            every { guild.upsertCommand(capture(published)) } returns upsertAction

            JdaGuildCommandRegistrar(jda).register(guildId, command)

            val data = published.captured
            assertEquals("play", data.name)
            assertEquals(PlayerCommandDefinitions.PLAY_COMMAND_NAME, data.name)
            assertEquals(
                localization.getDefaultString(PlayerMessageKeys.PLAY_COMMAND_DESCRIPTION),
                data.description,
            )
            assertEquals(DefaultMemberPermissions.ENABLED, data.defaultPermissions)

            val option = data.options.single()
            assertEquals(OptionType.STRING, option.type)
            assertEquals(PlayerCommandDefinitions.PLAY_OPTION_INPUT, option.name)
            assertEquals(
                localization.getDefaultString(PlayerMessageKeys.PLAY_COMMAND_INPUT_DESCRIPTION),
                option.description,
            )
            assertTrue(option.isRequired)
            assertFalse(option.isAutoComplete)
            verify(exactly = 0) { jda.upsertCommand(any<CommandData>()) }
        }

    private fun successfulUpsertAction(): RestAction<Command> {
        val action: RestAction<Command> = mockk()
        every { action.queue(any(), any()) } answers {
            firstArg<Consumer<Command?>>().accept(null)
        }
        return action
    }

    private class EmptyLocaleRepository : LocaleRepository {
        override suspend fun getGuildLocale(guildId: GuildId): LocaleCode? = null

        override suspend fun setGuildLocale(guildId: GuildId, locale: LocaleCode) =
            error("definition access must not persist")

        override suspend fun removeGuildLocale(guildId: GuildId) =
            error("definition access must not persist")

        override suspend fun getAllGuildLocales(): Map<GuildId, LocaleCode> = emptyMap()
    }
}
