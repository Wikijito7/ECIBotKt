package es.wokis.jda

import es.wokis.commands.locale.LocaleCommandHandler
import es.wokis.commands.neutral.GuildCommandRegistrar
import es.wokis.domain.locale.GetGuildLocaleUseCase
import es.wokis.domain.locale.SetGuildLocaleUseCase
import es.wokis.domain.model.GuildId
import es.wokis.jda.command.JdaCommandRouter
import es.wokis.jda.command.JdaComponentRouter
import es.wokis.jda.command.JdaGuildCommandRegistrar
import es.wokis.jda.command.JdaLocaleAutocompleteListener
import es.wokis.jda.player.JdaPlayCommand
import es.wokis.jda.player.JdaPlayerButtonHandler
import es.wokis.jda.player.JdaPlayerCommandController
import es.wokis.jda.player.JdaPlayerControlCommand
import es.wokis.jda.player.JdaPlayerRuntime
import es.wokis.jda.player.PLAYER_BUTTON_IDS
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
 * Isolated JDA bootstrap.
 *
 * DAVE preflight runs before the gateway client exists. Commands are upserted guild-scoped into
 * every guild joined at startup; nothing is registered globally. The token comes from the shared
 * `./data/config.json` and is never rendered.
 *
 * Startup and cleanup failures produce a non-zero exit; every cleanup step is attempted even when
 * another fails.
 */
fun main() {
    val exitCode = try {
        runBootstrap(JdaRuntimeConfig.fromConfig(loadSharedConfig()))
    } catch (configurationError: JdaRuntimeConfigException) {
        LOG.error("Invalid JDA runtime configuration: {}", configurationError.message)
        EXIT_FAILURE
    }

    if (exitCode != EXIT_SUCCESS) {
        exitProcess(exitCode)
    }
}

/**
 * Runs the runtime with an already validated [config]; a cleanup-step factory failure still runs
 * the production steps and is recorded. Fatal [Error]s and [CancellationException]s are rethrown.
 *
 * @return [EXIT_SUCCESS] only on a normal shutdown with every cleanup step succeeding.
 */
internal fun runBootstrap(
    config: JdaRuntimeConfig,
    davePreflight: DavePreflight = NativeDavePreflight,
    clientFactory: JdaClientFactory = DefaultJdaClientFactory,
    cleanupStepsFactory: (BootstrapCleanupTargets) -> List<BootstrapCleanupStep> =
        ::defaultBootstrapCleanupSteps,
): Int {
    var router: JdaCommandRouter? = null
    var componentRouter: JdaComponentRouter? = null
    var jda: JDA? = null
    var shutdownHook: Thread? = null
    var playerRuntime: JdaPlayerRuntime? = null
    var primaryFailure: Throwable? = null

    try {
        val client = buildGatewayClient(config, davePreflight, clientFactory)
        jda = client

        playerRuntime = JdaPlayerRuntime(sources = config.sources)

        val commands = buildCommands(client, playerRuntime)
        router = commands.router
        componentRouter = commands.componentRouter
        client.addEventListener(commands.router)
        client.addEventListener(commands.componentRouter)
        client.addEventListener(JdaLocaleAutocompleteListener())

        shutdownHook = Thread({ client.shutdown() }, "jda-shutdown").also {
            Runtime.getRuntime().addShutdownHook(it)
        }

        client.awaitReady()

        val joinedGuildIds = client.guilds.map { GuildId(it.idLong.toULong()) }
        runBlocking {
            val registrar = JdaGuildCommandRegistrar(client)
            joinedGuildIds.forEach { guildId ->
                registerLocaleCommand(registrar, guildId, commands.localizationService)
                registerPlayerCommands(registrar, guildId, commands.playCommand, commands.controlCommand)
            }
        }

        if (joinedGuildIds.isEmpty()) {
            LOG.warn(
                "JDA ready as {} but the client is not a member of any guild; no command registered",
                client.selfUser.name,
            )
        }
        LOG.info(
            "JDA ready as {}; guild-scoped /{}, /{} and /{} registered in {} guild(s); " +
                "intents=[GUILD_VOICE_STATES]",
            client.selfUser.name,
            commands.localeCommand.name,
            commands.playCommand.name,
            commands.controlCommand.name,
            joinedGuildIds.size,
        )

        client.awaitShutdown()
    } catch (error: Throwable) {
        primaryFailure = error
    }

    val targets = BootstrapCleanupTargets(
        jda = jda,
        router = router,
        componentRouter = componentRouter,
        shutdownHook = shutdownHook,
        playerRuntime = playerRuntime,
    )
    val cleanupReport = runBootstrapCleanupWithFallback(cleanupStepsFactory, targets)

    return finishBootstrap(primaryFailure, cleanupReport)
}

private class BootstrapCommands(
    val localeCommand: LocaleCommandHandler,
    val localizationService: GuildLocalizationService,
    val playCommand: JdaPlayCommand,
    val controlCommand: JdaPlayerControlCommand,
    val router: JdaCommandRouter,
    val componentRouter: JdaComponentRouter,
)

private fun buildCommands(client: JDA, playerRuntime: JdaPlayerRuntime): BootstrapCommands {
    val localeRepository = LocalJsonLocaleRepository(LocaleJson.json)
    val localizationService = GuildLocalizationService(GetGuildLocaleUseCase(localeRepository))
    val localeCommand = LocaleCommandHandler(
        setGuildLocaleUseCase = SetGuildLocaleUseCase(localeRepository),
        localizationService = localizationService,
    )
    val playerController = JdaPlayerCommandController(
        jda = client,
        runtime = playerRuntime,
        localizationService = localizationService,
    )
    val playCommand = JdaPlayCommand(playerController, localizationService)
    val controlCommand = JdaPlayerControlCommand(playerController, localizationService)

    return BootstrapCommands(
        localeCommand = localeCommand,
        localizationService = localizationService,
        playCommand = playCommand,
        controlCommand = controlCommand,
        router = JdaCommandRouter(listOf(localeCommand, playCommand, controlCommand)),
        componentRouter = JdaComponentRouter(
            PLAYER_BUTTON_IDS.map { id ->
                JdaPlayerButtonHandler(
                    id = id,
                    jda = client,
                    runtime = playerRuntime,
                    controller = playerController,
                )
            },
        ),
    )
}

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

internal suspend fun registerLocaleCommand(
    registrar: GuildCommandRegistrar,
    guildId: GuildId,
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
    registrar.register(guildId, definition)
}

internal suspend fun registerPlayerCommands(
    registrar: GuildCommandRegistrar,
    guildId: GuildId,
    playCommand: JdaPlayCommand,
    controlCommand: JdaPlayerControlCommand,
) {
    playCommand.registerIn(registrar, guildId)
    controlCommand.registerIn(registrar, guildId)
}
