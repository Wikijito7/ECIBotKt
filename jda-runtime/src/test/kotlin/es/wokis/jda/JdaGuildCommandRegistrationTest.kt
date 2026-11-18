package es.wokis.jda

import es.wokis.commands.locale.LocaleCommandHandler
import es.wokis.commands.player.PlayerCommandDefinitions
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
import io.mockk.mockkStatic
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
import net.dv8tion.jda.api.requests.RestAction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.function.Consumer

/**
 * Startup registration contract: every command is upserted guild-scoped into **every** guild the
 * client is in once the gateway is ready. There is deliberately no `GuildJoinEvent` handler, so a
 * runtime join waits for the next restart; the static environment policy test guards that absence.
 */
class JdaGuildCommandRegistrationTest {

    @Test
    fun `Given two joined guilds When the runtime becomes ready Then each guild receives the twelve guild commands`() {
        val fixture = RegistrationFixture(guildIds = listOf(FIRST_GUILD_ID, SECOND_GUILD_ID))

        val exitCode = fixture.run()

        assertEquals(0, exitCode)
        assertEquals(setOf(FIRST_GUILD_ID, SECOND_GUILD_ID), fixture.published.keys)
        fixture.published.forEach { (guildId, commands) ->
            assertEquals(
                EXPECTED_COMMANDS,
                commands.map { it.name }.toSet(),
                "guild $guildId did not receive every command",
            )
            assertEquals(EXPECTED_COMMANDS.size, commands.size, "guild $guildId received a duplicate command")
        }

        // Never through JDA's application-wide registration route.
        verify(exactly = 0) { fixture.jda.upsertCommand(any<CommandData>()) }
        verify(exactly = 0) { fixture.jda.upsertCommand(any<String>(), any<String>()) }
    }

    @Test
    fun `Given two joined guilds When commands are registered Then the admin commands keep the administrator permission`() {
        val fixture = RegistrationFixture(guildIds = listOf(FIRST_GUILD_ID, SECOND_GUILD_ID))
        val administrator = DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR)

        fixture.run()

        fixture.published.forEach { (guildId, commands) ->
            val byName = commands.associateBy { it.name }
            ADMIN_ONLY_COMMANDS.forEach { commandName ->
                val command = requireNotNull(byName[commandName]) {
                    "guild $guildId is missing the admin command /$commandName"
                }
                assertEquals(
                    administrator,
                    command.defaultPermissions,
                    "/$commandName must stay administrator-only in guild $guildId",
                )
            }
        }
    }

    @Test
    fun `Given two joined guilds When commands are registered Then the public player commands are enabled`() {
        val fixture = RegistrationFixture(guildIds = listOf(FIRST_GUILD_ID, SECOND_GUILD_ID))

        fixture.run()

        fixture.published.forEach { (guildId, commands) ->
            val byName = commands.associateBy { it.name }
            PUBLIC_PLAYER_COMMANDS.forEach { commandName ->
                val command = requireNotNull(byName[commandName]) {
                    "guild $guildId is missing the public command /$commandName"
                }
                assertEquals(
                    DefaultMemberPermissions.ENABLED,
                    command.defaultPermissions,
                    "/$commandName must be public in guild $guildId",
                )
            }
            assertEquals(
                setOf(
                    "play",
                    "player",
                    "skip",
                    "shuffle",
                    "next",
                    "queue",
                    "disconnect",
                    "reconnect",
                    "sound",
                    "sounds",
                    "tts",
                ),
                PUBLIC_PLAYER_COMMANDS,
                "guild $guildId names drifted",
            )
            assertEquals(11, PUBLIC_PLAYER_COMMANDS.size)
        }
    }

    @Test
    fun `Given two joined guilds When commands are registered Then only next declares its required track option`() {
        val fixture = RegistrationFixture(guildIds = listOf(FIRST_GUILD_ID, SECOND_GUILD_ID))

        fixture.run()

        fixture.published.forEach { (guildId, commands) ->
            val byName = commands.associateBy { it.name }
            val nextOption = (requireNotNull(byName["next"]) as SlashCommandData).options.single()
            assertEquals("track", nextOption.name)
            assertTrue(nextOption.isRequired, "/next must keep the required track option in guild $guildId")
            assertFalse(nextOption.isAutoComplete, "/next must not autocomplete the track option in guild $guildId")
            val soundOption = (requireNotNull(byName["sound"]) as SlashCommandData).options.single()
            assertEquals("name", soundOption.name)
            assertTrue(soundOption.isRequired, "/sound must keep the required name option in guild $guildId")
            assertTrue(soundOption.isAutoComplete, "/sound must autocomplete the name option in guild $guildId")
            val ttsOption = (requireNotNull(byName["tts"]) as SlashCommandData).options.single()
            assertEquals("message", ttsOption.name)
            assertTrue(ttsOption.isRequired, "/tts must keep the required message option in guild $guildId")
            assertFalse(ttsOption.isAutoComplete, "/tts must not autocomplete its message option in guild $guildId")
            val optionless = PUBLIC_PLAYER_COMMANDS - "next" - "play" - "player" - "sound" - "tts"
            optionless.forEach { commandName ->
                val command = requireNotNull(byName[commandName]) {
                    "guild $guildId is missing the optionless command /$commandName"
                }
                assertTrue(
                    (command as SlashCommandData).options.isEmpty(),
                    "/$commandName must stay optionless in guild $guildId",
                )
            }
        }
    }

    @Test
    fun `Given a client without guilds When the runtime becomes ready Then no command is registered anywhere`() {
        val fixture = RegistrationFixture(guildIds = emptyList())

        val exitCode = fixture.run()

        assertEquals(0, exitCode)
        assertTrue(fixture.published.isEmpty())
        verify(exactly = 0) { fixture.jda.upsertCommand(any<CommandData>()) }
        verify(exactly = 0) { fixture.jda.upsertCommand(any<String>(), any<String>()) }
    }

    @Test
    fun `Given a guild rejected its command upsert When registering Then the runtime exits failed without a global fallback`() {
        val fixture = RegistrationFixture(
            guildIds = listOf(FIRST_GUILD_ID, SECOND_GUILD_ID),
            failingGuildId = FIRST_GUILD_ID,
        )

        val exitCode = fixture.run()

        assertEquals(1, exitCode)
        verify(exactly = 0) { fixture.jda.upsertCommand(any<CommandData>()) }
        verify(exactly = 0) { fixture.jda.upsertCommand(any<String>(), any<String>()) }
    }

    private class RegistrationFixture(
        guildIds: List<Long>,
        failingGuildId: Long? = null,
    ) {
        val jda: JDA = mockk()
        val published: MutableMap<Long, MutableList<CommandData>> = linkedMapOf()
        private val runtime: Runtime = mockk()
        private val config: JdaRuntimeConfig = JdaRuntimeConfig.fromConfig(sharedConfig())

        init {
            val selfUser: SelfUser = mockk()
            every { selfUser.name } returns "Fixture Bot"
            every { jda.selfUser } returns selfUser
            every { jda.addEventListener(*anyVararg()) } just Runs
            every { jda.awaitReady() } returns jda
            every { jda.awaitShutdown() } returns true

            val guilds = guildIds.map { jdaId -> guild(jdaId, failingGuildId == jdaId) }
            every { jda.guilds } returns guilds
        }

        fun run(): Int {
            mockkStatic(Runtime::class)
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
                unmockkStatic(Runtime::class)
            }
        }

        private fun guild(jdaId: Long, failing: Boolean): Guild {
            val guild: Guild = mockk()
            every { guild.idLong } returns jdaId
            if (failing) {
                val rejected: RestAction<Command> = mockk()
                every { rejected.queue(any(), any()) } answers {
                    secondArg<Consumer<Throwable>>().accept(IllegalStateException("guild upsert rejected"))
                }
                every { guild.upsertCommand(any<CommandData>()) } returns rejected
            } else {
                val commands = mutableListOf<CommandData>()
                published[jdaId] = commands
                val accepted: RestAction<Command> = mockk()
                every { accepted.queue(any(), any()) } answers {
                    firstArg<Consumer<Command?>>().accept(null)
                }
                every { guild.upsertCommand(capture(commands)) } returns accepted
            }
            every { jda.getGuildById(jdaId) } returns guild
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
        )
        private val EXPECTED_COMMANDS = ADMIN_ONLY_COMMANDS + PUBLIC_PLAYER_COMMANDS

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
