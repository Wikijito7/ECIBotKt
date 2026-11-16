package es.wokis.jda

import es.wokis.commands.locale.LocaleCommandHandler
import es.wokis.commands.neutral.GuildCommandRegistrar
import es.wokis.commands.voicewalkthrough.JdaVoiceWalkthroughCommand
import es.wokis.commands.walkthrough.WalkthroughCommand
import es.wokis.domain.locale.GetGuildLocaleUseCase
import es.wokis.domain.locale.SetGuildLocaleUseCase
import es.wokis.jda.command.JdaCommandRouter
import es.wokis.jda.command.JdaGuildCommandRegistrar
import es.wokis.jda.command.JdaLocaleAutocompleteListener
import es.wokis.jda.player.JdaPlayCommand
import es.wokis.jda.player.JdaPlayerCommandController
import es.wokis.jda.player.JdaPlayerControlCommand
import es.wokis.jda.player.JdaPlayerRuntime
import es.wokis.jda.voice.JdaVoiceSessionPort
import es.wokis.jda.voice.OpusPacketFrameSource
import es.wokis.localization.GuildLocalizationService
import es.wokis.localization.LocaleMessageKeys
import es.wokis.repositories.locale.LocalJsonLocaleRepository
import es.wokis.repositories.locale.LocaleJson
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
 * registers the diagnostics plus the guild-scoped `/locale`, `/jda-play` and `/jda-player`
 * commands. No global command and no privileged intent is used. The root Kord runtime is not
 * involved.
 *
 * The Discord token and every player provider setting are read from the shared
 * `./data/config.json` file (same schema as the root Kord runtime); only the development guild id
 * and an optional `JDA_CONFIG_PATH` come from the environment. The configuration is loaded once —
 * there is no reload or watch behaviour, so a change requires a restart.
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
    var playerRuntime: JdaPlayerRuntime? = null
    var primaryFailure: Throwable? = null

    try {
        // Fail fast before any gateway login: no passthrough fallback is allowed.
        val client = buildGatewayClient(config, davePreflight, clientFactory)
        jda = client

        // Manual wiring for the isolated runtime (no Koin): one shared player engine and one
        // long-lived voice adapter per guild, created lazily when a player command arrives. The
        // source providers come from the same shared configuration file the root Kord runtime reads.
        playerRuntime = JdaPlayerRuntime(sources = config.sources)

        val commands = buildCommands(client, playerRuntime)
        router = commands.router
        client.addEventListener(commands.router)
        client.addEventListener(JdaLocaleAutocompleteListener())

        shutdownHook = Thread({ client.shutdown() }, "jda-shutdown").also {
            Runtime.getRuntime().addShutdownHook(it)
        }

        client.awaitReady()

        runBlocking {
            val registrar = JdaGuildCommandRegistrar(client)
            registerWalkthroughCommand(registrar, config, commands.walkthrough)
            registerVoiceWalkthroughCommand(registrar, config, commands.voiceWalkthrough)
            registerLocaleCommand(registrar, config, commands.localizationService)
            registerPlayerCommands(registrar, config, commands.playCommand, commands.controlCommand)
        }

        LOG.info(
            "JDA ready as {}; guild-scoped /{}, /{}, /{}, /{} and /{} registered; intents=[GUILD_VOICE_STATES]",
            client.selfUser.name,
            commands.walkthrough.name,
            commands.voiceWalkthrough.name,
            commands.localeCommand.name,
            commands.playCommand.name,
            commands.controlCommand.name,
        )

        client.awaitShutdown()
    } catch (error: Throwable) {
        primaryFailure = error
    }

    val targets = BootstrapCleanupTargets(
        jda = jda,
        router = router,
        shutdownHook = shutdownHook,
        playerRuntime = playerRuntime,
    )
    val cleanupReport = runBootstrapCleanupWithFallback(cleanupStepsFactory, targets)

    return finishBootstrap(primaryFailure, cleanupReport)
}

/**
 * Everything the bootstrap publishes and later registers, assembled once.
 *
 * [router] is the single JDA listener that dispatches every neutral command handler; the remaining
 * members are kept so registration and the ready log reuse the exact instances that were wired,
 * without a second composition path.
 */
private class BootstrapCommands(
    val walkthrough: WalkthroughCommand,
    val voiceWalkthrough: JdaVoiceWalkthroughCommand,
    val localeCommand: LocaleCommandHandler,
    val localizationService: GuildLocalizationService,
    val playCommand: JdaPlayCommand,
    val controlCommand: JdaPlayerControlCommand,
    val router: JdaCommandRouter,
)

/**
 * Builds the neutral command handlers and the JDA router for one gateway client.
 *
 * Keeping this wiring out of [runBootstrap] leaves the startup/cleanup sequence readable while
 * adding no second composition root: the router still owns every handler, and the guild-scoped
 * registration below reuses these exact instances.
 */
private fun buildCommands(client: JDA, playerRuntime: JdaPlayerRuntime): BootstrapCommands {
    val walkthrough = WalkthroughCommand()
    val voiceWalkthrough = JdaVoiceWalkthroughCommand(
        voiceSession = JdaVoiceSessionPort(client),
        frameSourceFactory = { OpusPacketFrameSource.fromResource() },
    )
    val localeRepository = LocalJsonLocaleRepository(LocaleJson.json)
    val localizationService = GuildLocalizationService(GetGuildLocaleUseCase(localeRepository))
    val localeCommand = LocaleCommandHandler(
        setGuildLocaleUseCase = SetGuildLocaleUseCase(localeRepository),
        localizationService = localizationService,
    )
    val playerController = JdaPlayerCommandController(jda = client, runtime = playerRuntime)
    val playCommand = JdaPlayCommand(playerController)
    val controlCommand = JdaPlayerControlCommand(playerController)

    return BootstrapCommands(
        walkthrough = walkthrough,
        voiceWalkthrough = voiceWalkthrough,
        localeCommand = localeCommand,
        localizationService = localizationService,
        playCommand = playCommand,
        controlCommand = controlCommand,
        router = JdaCommandRouter(
            listOf(walkthrough, voiceWalkthrough, localeCommand, playCommand, controlCommand),
        ),
    )
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
            failBootstrap(primaryFailure, cleanupReport, "JDA bootstrap failed")
        }

        val cleanupFatal = cleanupReport.fatalFailure
        if (cleanupFatal != null) {
            failBootstrap(
                cleanupFatal,
                cleanupReport,
                "JDA bootstrap cleanup failed with a fatal error",
                suppressed = primaryFailure,
            )
        }

        cleanupReport.attachTo(primaryFailure)
        LOG.error("JDA bootstrap failed", primaryFailure)
        return EXIT_FAILURE
    }

    val cleanupFatal = cleanupReport.fatalFailure
    if (cleanupFatal != null) {
        failBootstrap(cleanupFatal, cleanupReport, "JDA bootstrap cleanup failed with a fatal error")
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

private fun failBootstrap(
    failure: Throwable,
    cleanupReport: BootstrapCleanupReport,
    message: String,
    suppressed: Throwable? = null,
): Nothing {
    cleanupReport.attachTo(failure)
    if (suppressed != null && suppressed !== failure && !failure.suppressed.contains(suppressed)) {
        failure.addSuppressed(suppressed)
    }
    LOG.error(message, failure)
    throw failure
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

/**
 * Registers the guild-scoped `/locale` command through [registrar] using validated [config].
 *
 * Descriptions use the shared catalog's default (`en-US`) strings plus every translation the catalog
 * actually provides (currently `es-ES`). The definition is admin-only, has one required
 * autocomplete string option, and is only ever published into the configured guild. Global
 * command-registration parity remains a Phase 6 requirement.
 */
internal suspend fun registerLocaleCommand(
    registrar: GuildCommandRegistrar,
    config: JdaRuntimeConfig,
    localizationService: GuildLocalizationService,
) {
    val descriptionKey = LocaleMessageKeys.LOCALE_COMMAND_DESCRIPTION
    val optionDescriptionKey = LocaleMessageKeys.LOCALE_COMMAND_INPUT_DESCRIPTION
    val definition = LocaleCommandHandler.definition(
        description = localizationService.getDefaultString(descriptionKey),
        optionDescription = localizationService.getDefaultString(optionDescriptionKey),
        descriptionLocalizations = localizationService.getLocalizations(descriptionKey),
        optionDescriptionLocalizations = localizationService.getLocalizations(optionDescriptionKey),
    )
    registrar.register(config.guildId, definition)
}

/**
 * Registers the minimal Phase 4 staging commands through [registrar] using validated [config].
 *
 * Both commands are administrator-only and are published **only** into [JdaRuntimeConfig.guildId];
 * there is no global registration path. They exist to drive the real player during the deferred
 * Phase 3 staging matrix and do not port the production command families, which remain Phase 4
 * scope.
 */
internal suspend fun registerPlayerCommands(
    registrar: GuildCommandRegistrar,
    config: JdaRuntimeConfig,
    playCommand: JdaPlayCommand,
    controlCommand: JdaPlayerControlCommand,
) {
    playCommand.registerIn(registrar, config.guildId)
    controlCommand.registerIn(registrar, config.guildId)
}
