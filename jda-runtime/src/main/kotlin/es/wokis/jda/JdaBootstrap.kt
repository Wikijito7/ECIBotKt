package es.wokis.jda

import es.wokis.commands.neutral.GuildCommandRegistrar
import es.wokis.commands.voicewalkthrough.JdaVoiceWalkthroughCommand
import es.wokis.commands.walkthrough.WalkthroughCommand
import es.wokis.jda.command.JdaCommandRouter
import es.wokis.jda.command.JdaGuildCommandRegistrar
import es.wokis.jda.voice.JdaVoiceSessionPort
import es.wokis.jda.voice.OpusPacketFrameSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import net.dv8tion.jda.api.JDA
import org.slf4j.LoggerFactory
import kotlin.system.exitProcess

private val LOG = LoggerFactory.getLogger("es.wokis.jda.JdaBootstrap")

private const val EXIT_SUCCESS = 0
private const val EXIT_FAILURE = 1

/**
 * Minimal isolated JDA bootstrap for the neutral command and voice boundaries.
 *
 * It performs the DAVE native/protocol preflight **before** creating the gateway client,
 * enables only the non-privileged `GUILD_VOICE_STATES` intent and the voice-state cache, and
 * registers both diagnostics guild-scoped. No global command and no privileged intent is
 * used. The root Kord runtime is not involved.
 *
 * The process exits with a non-zero code when configuration, DAVE or startup fails. Even then,
 * every applicable cleanup step is attempted independently after startup completes or fails
 * (voice disconnect, router scope, shutdown hook, JDA client), so one failing step cannot skip the
 * rest. Cleanup failures are surfaced and attached as suppressed to a primary startup failure; a
 * fatal cleanup failure still propagates. If startup otherwise succeeded, a cleanup failure produces
 * a failure exit code. The bot token is only handed to JDA's `JDABuilder`; it is never logged or
 * rendered.
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
 * [davePreflight] and [clientFactory] are narrow test seams with production defaults. The
 * preflight always runs first; when it throws, no client is created and no gateway login is
 * attempted. [cleanupStepsFactory] is an internal seam: it receives the resources that were
 * created and returns the cleanup steps to attempt, so cleanup ordering and failure aggregation can
 * be verified without relying on a live JDA's own shutdown behaviour. If it throws, the production
 * [defaultBootstrapCleanupSteps] still run for the created resources and the factory failure is
 * recorded in the report.
 *
 * Cleanup never stops at the first failure. Each step is attempted, and its exception is collected.
 * Cleanup failures are attached as suppressed to a preserved primary failure. When startup otherwise
 * succeeds, a cleanup failure is logged and reported as [EXIT_FAILURE]. Fatal [Error]s and
 * [CancellationException]s are rethrown instead of being turned into an ordinary exit code; a fatal
 * primary startup failure is preserved, and a fatal cleanup failure carries the primary as a
 * suppressed exception when it takes precedence.
 *
 * @return [EXIT_SUCCESS] after a normal JDA shutdown with every cleanup step succeeding,
 * [EXIT_FAILURE] when the DAVE preflight, startup or cleanup fails.
 */
internal fun runBootstrap(
    config: JdaRuntimeConfig,
    davePreflight: DavePreflight = NativeDavePreflight,
    clientFactory: JdaClientFactory = DefaultJdaClientFactory,
    cleanupStepsFactory: (BootstrapCleanupTargets) -> List<BootstrapCleanupStep> =
        ::defaultBootstrapCleanupSteps,
): Int {
    var router: JdaCommandRouter? = null
    var jda: JDA? = null
    var shutdownHook: Thread? = null
    var primaryFailure: Throwable? = null

    try {
        // Fail fast before any gateway login: no passthrough fallback is allowed.
        val client = buildGatewayClient(config, davePreflight, clientFactory)
        jda = client

        val walkthrough = WalkthroughCommand()
        val voiceWalkthrough = JdaVoiceWalkthroughCommand(
            voiceSession = JdaVoiceSessionPort(client),
            frameSourceFactory = { OpusPacketFrameSource.fromResource() },
        )
        val commandRouter = JdaCommandRouter(listOf(walkthrough, voiceWalkthrough))
        router = commandRouter
        client.addEventListener(commandRouter)

        shutdownHook = Thread({ client.shutdown() }, "jda-shutdown").also {
            Runtime.getRuntime().addShutdownHook(it)
        }

        client.awaitReady()

        runBlocking {
            val registrar = JdaGuildCommandRegistrar(client)
            registerWalkthroughCommand(registrar, config, walkthrough)
            registerVoiceWalkthroughCommand(registrar, config, voiceWalkthrough)
        }

        LOG.info(
            "JDA ready as {}; guild-scoped /{} and /{} registered; intents=[GUILD_VOICE_STATES]",
            client.selfUser.name,
            walkthrough.name,
            voiceWalkthrough.name,
        )

        client.awaitShutdown()
    } catch (error: Throwable) {
        primaryFailure = error
    }

    val targets = BootstrapCleanupTargets(jda = jda, router = router, shutdownHook = shutdownHook)
    val cleanupReport = runBootstrapCleanupWithFallback(cleanupStepsFactory, targets)

    return finishBootstrap(primaryFailure, cleanupReport)
}

/**
 * Runs the cleanup steps, falling back to [defaultBootstrapCleanupSteps] when the factory fails.
 *
 * A failing factory must not skip the shutdown work, so the production steps still receive every
 * created resource (voice disconnect, router, shutdown hook, JDA). The factory failure is recorded
 * first in the report: a successful startup is therefore never reported as clean, and
 * [finishBootstrap] applies the usual primary/fatal policy to it. The fallback is guarded as well:
 * when the production steps cannot even be built, both failures are recorded instead of aborting
 * the cleanup phase.
 */
private fun runBootstrapCleanupWithFallback(
    cleanupStepsFactory: (BootstrapCleanupTargets) -> List<BootstrapCleanupStep>,
    targets: BootstrapCleanupTargets,
): BootstrapCleanupReport {
    val steps = try {
        cleanupStepsFactory(targets)
    } catch (factoryFailure: Throwable) {
        LOG.error(
            "The bootstrap cleanup step factory failed; using the production cleanup steps",
            factoryFailure,
        )
        val fallbackSteps = try {
            defaultBootstrapCleanupSteps(targets)
        } catch (fallbackFailure: Throwable) {
            LOG.error("The production cleanup steps could not be built", fallbackFailure)
            return BootstrapCleanupReport(listOf(factoryFailure, fallbackFailure))
        }
        return runBootstrapCleanup(fallbackSteps).withLeadingFailure(factoryFailure)
    }

    return runBootstrapCleanup(steps)
}

/**
 * Reports the bootstrap outcome after cleanup, attaching cleanup failures to the preserved primary.
 *
 * Internal so the exit-code and suppression semantics can be verified without a live JDA; the
 * production caller is [runBootstrap].
 */
internal fun finishBootstrap(
    primaryFailure: Throwable?,
    cleanupReport: BootstrapCleanupReport,
): Int {
    if (primaryFailure != null) {
        if (primaryFailure.isFatalBootstrapFailure()) {
            cleanupReport.attachTo(primaryFailure)
            LOG.error("JDA bootstrap failed", primaryFailure)
            throw primaryFailure
        }

        val cleanupFatal = cleanupReport.fatalFailure
        if (cleanupFatal != null) {
            cleanupReport.attachTo(cleanupFatal)
            if (cleanupFatal !== primaryFailure && !cleanupFatal.suppressed.contains(primaryFailure)) {
                cleanupFatal.addSuppressed(primaryFailure)
            }
            LOG.error("JDA bootstrap cleanup failed with a fatal error", cleanupFatal)
            throw cleanupFatal
        }

        cleanupReport.attachTo(primaryFailure)
        LOG.error("JDA bootstrap failed", primaryFailure)
        return EXIT_FAILURE
    }

    val cleanupFatal = cleanupReport.fatalFailure
    if (cleanupFatal != null) {
        cleanupReport.attachTo(cleanupFatal)
        LOG.error("JDA bootstrap cleanup failed with a fatal error", cleanupFatal)
        throw cleanupFatal
    }

    if (cleanupReport.failed) {
        val cleanupFailure = cleanupReport.failure
        if (cleanupFailure != null) {
            cleanupReport.attachTo(cleanupFailure)
            LOG.error("JDA bootstrap cleanup failed after a normal shutdown", cleanupFailure)
        } else {
            LOG.error("JDA bootstrap cleanup failed after a normal shutdown")
        }
        return EXIT_FAILURE
    }

    return EXIT_SUCCESS
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

/**
 * Registers the voice walkthrough command through [registrar] using the validated [config].
 *
 * The destination is always [JdaRuntimeConfig.guildId]; there is no global registration path.
 */
internal suspend fun registerVoiceWalkthroughCommand(
    registrar: GuildCommandRegistrar,
    config: JdaRuntimeConfig,
    voiceWalkthrough: JdaVoiceWalkthroughCommand,
) {
    voiceWalkthrough.registerIn(registrar, config.guildId)
}
