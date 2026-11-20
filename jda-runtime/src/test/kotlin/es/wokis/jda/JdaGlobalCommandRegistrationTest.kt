package es.wokis.jda

import es.wokis.commands.locale.LocaleCommandHandler
import es.wokis.commands.neutral.CommandDefinition
import es.wokis.commands.player.PlayerCommandDefinitions
import es.wokis.commands.radio.RadioCommandDefinitions
import es.wokis.jda.command.JdaGuildCommandPurger
import es.wokis.jda.player.JdaPlayCommand
import es.wokis.jda.player.JdaPlayerControlCommand
import es.wokis.services.config.Config
import es.wokis.services.config.DatabaseConfig
import es.wokis.services.config.DeezerConfig
import es.wokis.services.config.HuggingChatConfig
import es.wokis.services.config.KokoroConfig
import es.wokis.services.config.SpotifyConfig
import es.wokis.services.config.TidalConfig
import es.wokis.services.config.YouTubeConfig
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import io.mockk.verify
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.audio.AudioModuleConfig
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.SelfUser
import net.dv8tion.jda.api.interactions.commands.Command
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions
import net.dv8tion.jda.api.interactions.commands.build.CommandData
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.function.Consumer

/**
 * Startup registration contract after the global-only convergence: once the gateway is ready every
 * joined guild's command set is emptied, then the collected definitions are published as exactly
 * one global bulk overwrite. Guilds that join later are handled by [JdaGuildCommandPurger].
 */
class JdaGlobalCommandRegistrationTest {

    @Test
    fun `Given two joined guilds When the runtime becomes ready Then purge both before one global publish`() {
        val fixture = RegistrationFixture(guildIds = listOf(FIRST_GUILD_ID, SECOND_GUILD_ID))

        val exitCode = fixture.run()

        assertEquals(0, exitCode)
        assertEquals(
            listOf("ready", "guild-purge", "guild-purge", "global-publish"),
            fixture.events,
            "the boot order must stay: ready, purge every joined guild, then the single global PUT",
        )
        assertEquals(EXPECTED_COMMAND_ORDER, fixture.globalCommands.map { it.name })
        assertEquals(EXPECTED_COMMAND_ORDER.size, fixture.globalCommands.size)
        verify(exactly = 0) { fixture.jda.getGuildById(any<Long>()) }
    }

    @Test
    fun `Given two joined guilds When commands are published Then the admin commands keep the administrator permission`() {
        val fixture = RegistrationFixture(guildIds = listOf(FIRST_GUILD_ID, SECOND_GUILD_ID))
        val administrator = DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR)

        fixture.run()

        val byName = fixture.globalCommands.associateBy { it.name }
        ADMIN_ONLY_COMMANDS.forEach { commandName ->
            val command = requireNotNull(byName[commandName]) { "the global set is missing /$commandName" }
            assertEquals(administrator, command.defaultPermissions, "/$commandName must stay administrator-only")
        }
    }

    @Test
    fun `Given two joined guilds When commands are published Then the public player commands are enabled`() {
        val fixture = RegistrationFixture(guildIds = listOf(FIRST_GUILD_ID, SECOND_GUILD_ID))

        fixture.run()

        val byName = fixture.globalCommands.associateBy { it.name }
        PUBLIC_PLAYER_COMMANDS.forEach { commandName ->
            val command = requireNotNull(byName[commandName]) { "the global set is missing /$commandName" }
            assertEquals(DefaultMemberPermissions.ENABLED, command.defaultPermissions, "/$commandName must be public")
        }
        assertEquals(12, PUBLIC_PLAYER_COMMANDS.size)
    }

    @Test
    fun `Given the global set When inspected Then radio publishes Kord's group shape`() {
        val fixture = RegistrationFixture(guildIds = listOf(FIRST_GUILD_ID))

        fixture.run()

        val radio = requireNotNull(
            fixture.globalCommands.firstOrNull { it.name == "radio" } as? SlashCommandData,
        ) { "the global set is missing /radio" }
        assertEquals(DefaultMemberPermissions.ENABLED, radio.defaultPermissions, "/radio must stay public")
        assertTrue(radio.options.isEmpty(), "/radio must not publish a top-level option")
        assertTrue(radio.description.isNotBlank())
        assertEquals(listOf("play", "list", "countrycodes", "random"), radio.subcommands.map { it.name })
        val search = radio.subcommandGroups.single()
        assertEquals("search", search.name)
        assertEquals(listOf("name", "countrycode"), search.subcommands.map { it.name })

        val playOption = radio.subcommands.first { it.name == "play" }.options.single()
        assertEquals("radio", playOption.name)
        assertTrue(playOption.isRequired)
        assertTrue(playOption.isAutoComplete)
        assertTrue(radio.subcommands.filterNot { it.name == "play" }.all { it.options.isEmpty() })

        val nameOption = search.subcommands.first { it.name == "name" }.options.single()
        assertEquals("name", nameOption.name)
        assertTrue(nameOption.isRequired)
        assertFalse(nameOption.isAutoComplete)

        val countryCodeOption = search.subcommands.first { it.name == "countrycode" }.options.single()
        assertEquals("countrycode", countryCodeOption.name)
        assertTrue(countryCodeOption.isRequired)
        assertTrue(countryCodeOption.isAutoComplete)
    }

    @Test
    fun `Given the global set When inspected Then only next sound and tts declare an option`() {
        val fixture = RegistrationFixture(guildIds = listOf(FIRST_GUILD_ID))

        fixture.run()

        val byName = fixture.globalCommands.associateBy { it.name }
        val nextOption = (requireNotNull(byName["next"]) as SlashCommandData).options.single()
        assertEquals("track", nextOption.name)
        assertTrue(nextOption.isRequired, "/next must keep the required track option")
        assertFalse(nextOption.isAutoComplete, "/next must not autocomplete the track option")

        val soundOption = (requireNotNull(byName["sound"]) as SlashCommandData).options.single()
        assertEquals("name", soundOption.name)
        assertTrue(soundOption.isRequired, "/sound must keep the required name option")
        assertTrue(soundOption.isAutoComplete, "/sound must autocomplete the name option")

        val ttsOption = (requireNotNull(byName["tts"]) as SlashCommandData).options.single()
        assertEquals("message", ttsOption.name)
        assertTrue(ttsOption.isRequired, "/tts must keep the required message option")
        assertFalse(ttsOption.isAutoComplete, "/tts must not autocomplete its message option")

        (PUBLIC_PLAYER_COMMANDS - "play" - "player" - "next" - "sound" - "tts").forEach { commandName ->
            val command = requireNotNull(byName[commandName]) { "the global set is missing /$commandName" }
            assertTrue(
                (command as SlashCommandData).options.isEmpty(),
                "/$commandName must stay optionless",
            )
        }
    }

    @Test
    fun `Given a client without guilds When the runtime becomes ready Then publish only the global set`() {
        val fixture = RegistrationFixture(guildIds = emptyList())

        val exitCode = fixture.run()

        assertEquals(0, exitCode)
        assertEquals(listOf("ready", "global-publish"), fixture.events)
        assertEquals(EXPECTED_COMMAND_ORDER, fixture.globalCommands.map { it.name })
        verify(exactly = 0) { fixture.jda.getGuildById(any<Long>()) }
    }

    @Test
    fun `Given the runtime becomes ready When listeners are registered Then the routers and the guild purger are present`() {
        val fixture = RegistrationFixture(guildIds = listOf(FIRST_GUILD_ID))

        fixture.run()

        assertEquals(4, fixture.listeners.size, "the two routers, the autocomplete router and the purger")
        assertTrue(
            fixture.listeners.any { it is JdaGuildCommandPurger },
            "the late-join purger listener must stay registered",
        )
    }

    @Test
    fun `Given the global publish is rejected When registering Then the runtime exits failed`() {
        val fixture = RegistrationFixture(guildIds = listOf(FIRST_GUILD_ID), failingGlobalPublish = true)

        val exitCode = fixture.run()

        assertEquals(1, exitCode)
        assertEquals(listOf("ready", "guild-purge", "global-publish"), fixture.events)
    }

    @Test
    fun `Given a guild purge is rejected When registering Then the global set is never published`() {
        val fixture = RegistrationFixture(
            guildIds = listOf(FIRST_GUILD_ID, SECOND_GUILD_ID),
            failingGuildId = FIRST_GUILD_ID,
        )

        val exitCode = fixture.run()

        assertEquals(1, exitCode)
        assertEquals(listOf("ready"), fixture.events, "the purge must fail before the global PUT")
        assertTrue(fixture.globalCommands.isEmpty(), "a failed purge must not be followed by a global publish")
    }

    @Test
    fun `Given a conflicting definition pair When collecting Then the runtime exits failed before any purge or publish`() {
        val fixture = RegistrationFixture(
            guildIds = listOf(FIRST_GUILD_ID),
            conflictingDefinitionPair = true,
        )

        val exitCode = fixture.run()

        assertEquals(1, exitCode, "a collected conflict must fail the boot instead of publishing a partial set")
        assertEquals(
            listOf("ready"),
            fixture.events,
            "the conflict must abort before the guild purge and the global PUT",
        )
        assertTrue(fixture.globalCommands.isEmpty(), "a conflicting definition set must never be published")
        verify(exactly = 0) { fixture.jda.updateCommands() }
        fixture.guildMocks.forEach { guild ->
            verify(exactly = 0) { guild.updateCommands() }
        }
    }

    private class RegistrationFixture(
        guildIds: List<Long>,
        private val failingGuildId: Long? = null,
        failingGlobalPublish: Boolean = false,
        private val conflictingDefinitionPair: Boolean = false,
    ) {
        val jda: JDA = mockk()
        val events: MutableList<String> = mutableListOf()
        val listeners: MutableList<Any> = mutableListOf()
        val globalCommands: MutableList<CommandData> = mutableListOf()
        val guildMocks: MutableList<Guild> = mutableListOf()
        private val runtime: Runtime = mockk()
        private val config: JdaRuntimeConfig = JdaRuntimeConfig.fromConfig(sharedConfig())

        init {
            val selfUser: SelfUser = mockk()
            every { selfUser.name } returns "Fixture Bot"
            every { jda.selfUser } returns selfUser
            every { jda.addEventListener(*anyVararg()) } answers {
                invocation.args.forEach { argument ->
                    if (argument is Array<*>) {
                        listeners.addAll(argument.filterNotNull())
                    } else if (argument != null) {
                        listeners += argument
                    }
                }
            }
            every { jda.awaitReady() } answers {
                events += "ready"
                jda
            }
            every { jda.awaitShutdown() } returns true
            every { jda.guilds } returns guildIds.map(::guild)
            every { jda.updateCommands() } returns globalAction(failingGlobalPublish)
        }

        fun run(): Int {
            mockkStatic(Runtime::class)
            if (conflictingDefinitionPair) {
                // GIVEN /play collects a different definition under /player's name, the real
                // collecting registrar must reject the pair at boot instead of silently deduping.
                mockkObject(PlayerCommandDefinitions)
                every { PlayerCommandDefinitions.play(any()) } returns CommandDefinition(
                    name = PlayerCommandDefinitions.PLAYER_COMMAND_NAME,
                    description = "Conflicting /player definition collected by /play",
                )
            }
            try {
                every { Runtime.getRuntime() } returns runtime
                every { runtime.addShutdownHook(any()) } just Runs

                return runBootstrap(
                    config = config,
                    davePreflight = DavePreflight { AudioModuleConfig() },
                    clientFactory = JdaClientFactory { _, _ -> jda },
                    // Cleanup is covered elsewhere; this fixture only observes registration.
                    cleanupStepsFactory = { emptyList() },
                )
            } finally {
                if (conflictingDefinitionPair) {
                    unmockkObject(PlayerCommandDefinitions)
                }
                unmockkStatic(Runtime::class)
            }
        }

        private fun globalAction(failing: Boolean): CommandListUpdateAction {
            val action: CommandListUpdateAction = mockk()
            every { action.addCommands(any<Collection<CommandData>>()) } answers {
                globalCommands.addAll(firstArg<Collection<CommandData>>())
                action
            }
            every { action.queue(any(), any()) } answers {
                events += "global-publish"
                if (failing) {
                    secondArg<Consumer<Throwable>>().accept(IllegalStateException("global publish rejected"))
                } else {
                    firstArg<Consumer<List<Command>?>>().accept(emptyList())
                }
            }
            return action
        }

        private fun guild(jdaId: Long): Guild {
            val guild: Guild = mockk()
            every { guild.idLong } returns jdaId
            val action: CommandListUpdateAction = mockk()
            every { guild.updateCommands() } returns action
            every { action.queue(any(), any()) } answers {
                if (jdaId == failingGuildId) {
                    secondArg<Consumer<Throwable>>().accept(IllegalStateException("guild purge rejected"))
                } else {
                    events += "guild-purge"
                    firstArg<Consumer<List<Command>?>>().accept(emptyList())
                }
            }
            guildMocks += guild
            return guild
        }
    }

    private companion object {
        private const val FIRST_GUILD_ID = 111111111111111111L
        private const val SECOND_GUILD_ID = 222222222222222222L

        private val ADMIN_ONLY_COMMANDS = setOf(LocaleCommandHandler.COMMAND_NAME)
        private val PUBLIC_PLAYER_COMMANDS = setOf(
            JdaPlayCommand.COMMAND_NAME,
            JdaPlayerControlCommand.COMMAND_NAME,
            PlayerCommandDefinitions.SKIP_COMMAND_NAME,
            PlayerCommandDefinitions.SHUFFLE_COMMAND_NAME,
            PlayerCommandDefinitions.NEXT_COMMAND_NAME,
            PlayerCommandDefinitions.QUEUE_COMMAND_NAME,
            PlayerCommandDefinitions.DISCONNECT_COMMAND_NAME,
            PlayerCommandDefinitions.RECONNECT_COMMAND_NAME,
            PlayerCommandDefinitions.SOUND_COMMAND_NAME,
            PlayerCommandDefinitions.SOUNDS_COMMAND_NAME,
            PlayerCommandDefinitions.TTS_COMMAND_NAME,
            RadioCommandDefinitions.RADIO_COMMAND_NAME,
        )

        /** Collecting-registrar order, pinned end to end through the bootstrap. */
        private val EXPECTED_COMMAND_ORDER = listOf(
            LocaleCommandHandler.COMMAND_NAME,
            PlayerCommandDefinitions.PLAY_COMMAND_NAME,
            PlayerCommandDefinitions.PLAYER_COMMAND_NAME,
            PlayerCommandDefinitions.SKIP_COMMAND_NAME,
            PlayerCommandDefinitions.SHUFFLE_COMMAND_NAME,
            PlayerCommandDefinitions.NEXT_COMMAND_NAME,
            PlayerCommandDefinitions.QUEUE_COMMAND_NAME,
            PlayerCommandDefinitions.DISCONNECT_COMMAND_NAME,
            PlayerCommandDefinitions.RECONNECT_COMMAND_NAME,
            PlayerCommandDefinitions.SOUND_COMMAND_NAME,
            PlayerCommandDefinitions.SOUNDS_COMMAND_NAME,
            PlayerCommandDefinitions.TTS_COMMAND_NAME,
            RadioCommandDefinitions.RADIO_COMMAND_NAME,
        )

        private fun sharedConfig(): Config = Config(
            discordBotToken = "synthetic-registration-token",
            debug = false,
            database = DatabaseConfig(false, "db_user", "db_password", "db_name"),
            youtube = YouTubeConfig(false, null, null, null, null, null),
            huggingChat = HuggingChatConfig(false, "hugging_user", "hugging_password"),
            deezer = DeezerConfig(false, "", ""),
            spotify = SpotifyConfig(false, "", "", ""),
            tidal = TidalConfig(false, "ES", ""),
            kokoro = KokoroConfig("", "", 1.0f, ""),
        )
    }
}
