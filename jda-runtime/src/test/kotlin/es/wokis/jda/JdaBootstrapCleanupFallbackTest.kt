package es.wokis.jda

import es.wokis.jda.command.JdaCommandRouter
import es.wokis.jda.player.JdaPlayerRuntime
import es.wokis.player.engine.config.toPlayerSourcesConfig
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.isActive
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.audio.AudioModuleConfig
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.SelfUser
import net.dv8tion.jda.api.interactions.commands.Command
import net.dv8tion.jda.api.interactions.commands.build.CommandData
import net.dv8tion.jda.api.managers.AudioManager
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit
import java.util.function.Consumer

private const val FIXTURE_GUILD_JDA_ID = 987654321L

class JdaBootstrapCleanupFallbackTest {

    private val config = JdaRuntimeConfig.fromConfig(sharedJsonConfig())

    @Test
    fun `Given a failing cleanup step factory When bootstrapping fails Then report failure without swallowing the factory error`() {
        val preflight = DavePreflight { throw IllegalStateException("DAVE is unavailable") }
        val clientFactory = JdaClientFactory { _, _ ->
            error("a client must never be built after a failed preflight")
        }
        var observedTargets: BootstrapCleanupTargets? = null

        val exitCode = runBootstrap(config, preflight, clientFactory) { targets ->
            observedTargets = targets
            error("cleanup factory failed")
        }

        assertEquals(1, exitCode)
        assertEquals(BootstrapCleanupTargets(null, null, null), observedTargets)
    }

    @Test
    fun `Given a fatal startup failure When the cleanup step factory also fails Then rethrow the fatal failure`() {
        val fatal = AssertionError("fatal startup")
        val preflight = DavePreflight { throw fatal }
        val clientFactory = JdaClientFactory { _, _ ->
            error("a client must never be built after a failed preflight")
        }

        val thrown = assertThrows(AssertionError::class.java) {
            runBootstrap(config, preflight, clientFactory) {
                error("cleanup factory failed")
            }
        }

        assertSame(fatal, thrown)
        assertEquals("cleanup factory failed", thrown.suppressed.single().message)
    }

    @Test
    fun `Given a failing cleanup factory and created resources When startup succeeded Then the fallback cleans everything and fails`() {
        val fixture = BootstrapFixture(config)

        val exitCode = fixture.run {
            error("cleanup factory failed")
        }

        assertEquals(1, exitCode)
        val observedRouter = requireNotNull(fixture.observedRouter) {
            "the router created by the bootstrap must reach cleanup"
        }
        assertTrue(observedRouter.isClosed(), "the fallback must close the command router")
        assertSame(fixture.shutdownHook, fixture.observedHook, "the created hook must still be removed")
        assertEquals(
            listOf("voice-disconnect", "shutdown-hook-removed", "jda-shutdown"),
            fixture.events.dropWhile { it != "voice-disconnect" },
            "the fallback must attempt voice, hook and JDA cleanup in production order",
        )
        verify(exactly = 1) { fixture.audioManager.closeAudioConnection() }
        verify(exactly = 1) { fixture.jda.shutdown() }
    }

    @Test
    fun `Given a cleanup factory that throws an Error When startup succeeded Then the fallback runs and the Error is rethrown`() {
        val fixture = BootstrapFixture(config)
        val factoryFailure = AssertionError("fatal cleanup factory")

        val thrown = assertThrows(AssertionError::class.java) {
            fixture.run { throw factoryFailure }
        }

        assertSame(factoryFailure, thrown)
        assertTrue(factoryFailure.suppressed.isEmpty(), "a clean fallback must not add suppressions")
        assertEquals(
            listOf("voice-disconnect", "shutdown-hook-removed", "jda-shutdown"),
            fixture.events.dropWhile { it != "voice-disconnect" },
        )
    }

    @Test
    fun `Given cleanup factory throws cancellation When startup succeeds Then fallback runs and cancellation is rethrown`() {
        val fixture = BootstrapFixture(config)
        val factoryFailure = CancellationException("cleanup factory cancelled")

        val thrown = assertThrows(CancellationException::class.java) {
            fixture.run { throw factoryFailure }
        }

        assertSame(factoryFailure, thrown)
        assertEquals(
            listOf("voice-disconnect", "shutdown-hook-removed", "jda-shutdown"),
            fixture.events.dropWhile { it != "voice-disconnect" },
        )
    }

    @Test
    fun `Given a normal startup failure When the cleanup factory throws an Error Then the Error wins with the primary suppressed`() {
        val primary = IllegalStateException("startup failed after resources were created")
        val fixture = BootstrapFixture(config, awaitReadyFailure = primary)
        val factoryFailure = AssertionError("fatal cleanup factory")

        val thrown = assertThrows(AssertionError::class.java) {
            fixture.run { throw factoryFailure }
        }

        assertSame(factoryFailure, thrown)
        assertEquals(listOf(primary), factoryFailure.suppressed.toList())
        assertTrue(fixture.events.contains("voice-disconnect"), "the fallback must still clean the created resources")
    }

    @Test
    fun `Given normal startup failure When cleanup factory throws cancellation Then cancellation wins with primary suppressed`() {
        val primary = IllegalStateException("startup failed after resources were created")
        val fixture = BootstrapFixture(config, awaitReadyFailure = primary)
        val factoryFailure = CancellationException("cleanup factory cancelled")

        val thrown = assertThrows(CancellationException::class.java) {
            fixture.run { throw factoryFailure }
        }

        assertSame(factoryFailure, thrown)
        assertEquals(listOf(primary), factoryFailure.suppressed.toList())
    }

    @Test
    fun `Given a fatal startup failure When the cleanup factory also throws an Error Then the primary keeps precedence`() {
        val primary = AssertionError("fatal startup after resources were created")
        val fixture = BootstrapFixture(config, awaitReadyFailure = primary)
        val factoryFailure = AssertionError("fatal cleanup factory")

        val thrown = assertThrows(AssertionError::class.java) {
            fixture.run { throw factoryFailure }
        }

        assertSame(primary, thrown)
        assertEquals(listOf(factoryFailure), primary.suppressed.toList())
        assertTrue(fixture.events.contains("voice-disconnect"), "the fallback must still clean the created resources")
    }

    @Test
    fun `Given a shared config When bootstrapping Then its token and mapped sources reach the runtime`() {
        val sharedConfig = sharedJsonConfig(
            token = "bootstrap-token-sentinel",
            youtube = YouTubeConfig(
                enabled = true,
                oauth2Token = "bootstrap-oauth2-sentinel",
                poToken = null,
                visitorData = null,
                remoteCipherUrl = null,
                remoteCipherPassword = null,
            ),
        )
        val expectedSources = sharedConfig.toPlayerSourcesConfig()
        val config = JdaRuntimeConfig.fromConfig(sharedConfig)
        val fixture = BootstrapFixture(config)

        val exitCode = fixture.run { defaultBootstrapCleanupSteps(it) }

        assertEquals(0, exitCode)
        val clientConfig = requireNotNull(fixture.observedClientConfig) {
            "the mapped runtime config must reach the JDA client factory"
        }
        assertSame(config, clientConfig)
        assertEquals(sharedConfig.discordBotToken, clientConfig.token)
        assertEquals(expectedSources, clientConfig.sources)

        val runtime = requireNotNull(fixture.observedPlayerRuntime) {
            "the player runtime created by the bootstrap must reach cleanup"
        }
        val runtimeSources = JdaPlayerRuntime::class.java
            .getDeclaredField("sources")
            .apply { isAccessible = true }
            .get(runtime)
        assertSame(
            clientConfig.sources,
            runtimeSources,
            "the shared-config provider sources must be wired into the player runtime",
        )
    }

    private fun sharedJsonConfig(
        token: String = "fake-token",
        youtube: YouTubeConfig = YouTubeConfig(false, null, null, null, null, null),
    ): Config = Config(
        discordBotToken = token,
        debug = false,
        database = DatabaseConfig(false, "db_user", "db_password", "db_name"),
        youtube = youtube,
        huggingChat = HuggingChatConfig(false, "hugging_user", "hugging_password"),
        deezer = DeezerConfig(false, "", ""),
        spotify = SpotifyConfig(false, "", "", ""),
        tidal = TidalConfig(false, "ES", ""),
        kokoro = KokoroConfig("", "", 1.0f, ""),
    )

    private fun JdaCommandRouter.isClosed(): Boolean {
        val scope = JdaCommandRouter::class.java
            .getDeclaredField("scope")
            .apply { isAccessible = true }
            .get(this) as CoroutineScope
        return !scope.isActive
    }

    private class BootstrapFixture(
        private val config: JdaRuntimeConfig,
        awaitReadyFailure: Throwable? = null,
    ) {
        val events = mutableListOf<String>()
        val jda: JDA = mockk()
        val guild: Guild = mockk()
        val audioManager: AudioManager = mockk()
        val runtime: Runtime = mockk()
        val selfUser: SelfUser = mockk()
        private val guildPurgeAction: CommandListUpdateAction = mockk()
        private val globalPublishAction: CommandListUpdateAction = mockk()

        lateinit var shutdownHook: Thread
            private set

        var observedHook: Thread? = null
            private set

        var observedRouter: JdaCommandRouter? = null
            private set

        var observedClientConfig: JdaRuntimeConfig? = null
            private set

        var observedPlayerRuntime: JdaPlayerRuntime? = null
            private set

        init {
            every { jda.addEventListener(*anyVararg()) } just Runs
            every { jda.selfUser } returns selfUser
            every { selfUser.name } returns "Fixture Bot"
            every { jda.getGuildById(FIXTURE_GUILD_JDA_ID) } returns guild
            every { guild.idLong } returns FIXTURE_GUILD_JDA_ID
            every { guild.updateCommands() } returns guildPurgeAction
            every { guildPurgeAction.queue(any(), any()) } answers {
                events += "guild-purge"
                firstArg<Consumer<List<Command>?>>().accept(emptyList())
            }
            every { jda.updateCommands() } returns globalPublishAction
            every { globalPublishAction.addCommands(any<Collection<CommandData>>()) } returns globalPublishAction
            every { globalPublishAction.queue(any(), any()) } answers {
                events += "global-publish"
                firstArg<Consumer<List<Command>?>>().accept(emptyList())
            }
            every { jda.awaitShutdown() } answers {
                events += "await-shutdown"
                true
            }
            every { jda.status } returns JDA.Status.CONNECTED
            every { jda.guilds } returns listOf(guild)
            every { guild.audioManager } returns audioManager
            every { audioManager.isConnected } returns true
            every { audioManager.closeAudioConnection() } answers { events += "voice-disconnect" }
            every { jda.shutdown() } answers { events += "jda-shutdown" }
            every { jda.awaitShutdown(10L, TimeUnit.SECONDS) } returns true

            if (awaitReadyFailure == null) {
                every { jda.awaitReady() } answers {
                    events += "ready"
                    jda
                }
            } else {
                every { jda.awaitReady() } throws awaitReadyFailure
            }
        }

        fun run(cleanupStepsFactory: (BootstrapCleanupTargets) -> List<BootstrapCleanupStep>): Int {
            mockkStatic(Runtime::class)
            try {
                every { Runtime.getRuntime() } returns runtime
                every { runtime.addShutdownHook(any()) } answers {
                    shutdownHook = firstArg()
                    events += "shutdown-hook-added"
                }
                every { runtime.removeShutdownHook(any()) } answers {
                    observedHook = firstArg()
                    events += "shutdown-hook-removed"
                    true
                }

                return runBootstrap(
                    config = config,
                    davePreflight = DavePreflight { AudioModuleConfig() },
                    clientFactory = JdaClientFactory { clientConfig, _ ->
                        observedClientConfig = clientConfig
                        jda
                    },
                    cleanupStepsFactory = { targets ->
                        observedRouter = targets.router as? JdaCommandRouter
                        observedPlayerRuntime = targets.playerRuntime as? JdaPlayerRuntime
                        cleanupStepsFactory(targets)
                    },
                )
            } finally {
                unmockkStatic(Runtime::class)
            }
        }
    }
}
