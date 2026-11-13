package es.wokis.jda

import es.wokis.commands.neutral.GuildCommandRegistrar
import es.wokis.commands.walkthrough.WalkthroughCommand
import es.wokis.jda.command.JdaCommandRouter
import es.wokis.jda.command.JdaGuildCommandRegistrar
import kotlinx.coroutines.runBlocking
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.requests.GatewayIntent
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

private val LOG = LoggerFactory.getLogger("es.wokis.jda.JdaBootstrap")

private const val EXIT_SUCCESS = 0
private const val EXIT_FAILURE = 1
private const val SHUTDOWN_TIMEOUT_SECONDS = 10L

/**
 * Minimal isolated JDA bootstrap for the neutral command boundary.
 *
 * It registers exactly one guild-scoped command and never publishes a global command, so a
 * missing or wrong development guild fails fast instead of leaking commands application-wide.
 * No gateway intents are enabled: slash-command interactions do not require privileged intents.
 *
 * The process exits with a non-zero code when configuration or startup fails, but only after
 * [runBootstrap] has released the router scope, the shutdown hook and the JDA client in its
 * `finally` block. The bot token is only handed to [JDABuilder]; it is never logged or rendered.
 *
 * This entry point is intentionally separate from the root Kord runtime and is not part of
 * the root application or fat jar.
 */
fun main() {
    val exitCode = try {
        runBootstrap(JdaRuntimeConfig.fromEnv())
    } catch (configurationError: JdaRuntimeConfigException) {
        LOG.error("Invalid JDA runtime configuration: {}", configurationError.message)
        EXIT_FAILURE
    }

    if (exitCode != EXIT_SUCCESS) {
        exitProcess(exitCode)
    }
}

/**
 * Runs the isolated runtime with an already validated [config].
 *
 * The function fully owns the lifecycle of the router, the JDA shutdown hook and the JDA
 * client: everything is released in the `finally` block before the exit code is returned, so
 * no cleanup is skipped on a startup failure.
 *
 * @return [EXIT_SUCCESS] after a normal JDA shutdown, [EXIT_FAILURE] when startup fails.
 */
internal fun runBootstrap(config: JdaRuntimeConfig): Int {
    val walkthrough = WalkthroughCommand()
    val router = JdaCommandRouter(listOf(walkthrough))
    var jda: JDA? = null
    var shutdownHook: Thread? = null

    try {
        val client = JDABuilder.createLight(config.token, emptySet<GatewayIntent>())
            .addEventListeners(router)
            .build()
        jda = client

        shutdownHook = Thread({ client.shutdown() }, "jda-shutdown").also {
            Runtime.getRuntime().addShutdownHook(it)
        }

        client.awaitReady()

        runBlocking {
            registerWalkthroughCommand(JdaGuildCommandRegistrar(client), config, walkthrough)
        }

        LOG.info(
            "JDA ready as {} ({}); guild-scoped /{} registered in guild {}; no gateway intents enabled",
            client.selfUser.name,
            client.selfUser.id,
            walkthrough.name,
            config.guildId,
        )

        client.awaitShutdown()
        return EXIT_SUCCESS
    } catch (error: Throwable) {
        LOG.error("JDA bootstrap failed", error)
        return EXIT_FAILURE
    } finally {
        router.close()
        shutdownHook?.let { hook -> runCatching { Runtime.getRuntime().removeShutdownHook(hook) } }
        jda?.let { client ->
            if (client.status != JDA.Status.SHUTDOWN) {
                client.shutdown()
                if (!client.awaitShutdown(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    client.shutdownNow()
                }
            }
        }
    }
}

/**
 * Registers the walkthrough command through [registrar] using the validated [config].
 *
 * Kept separate from [runBootstrap] so the guild-only registration step can be exercised with a
 * fake [GuildCommandRegistrar], without logging in to Discord.
 */
internal suspend fun registerWalkthroughCommand(
    registrar: GuildCommandRegistrar,
    config: JdaRuntimeConfig,
    walkthrough: WalkthroughCommand = WalkthroughCommand(),
) {
    walkthrough.registerIn(registrar, config.guildId)
}
