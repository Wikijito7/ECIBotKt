package es.wokis.jda

import es.wokis.commands.disconnect.DisconnectCommandHandler
import es.wokis.commands.locale.LocaleAutocompleteHandler
import es.wokis.commands.locale.LocaleCommandHandler
import es.wokis.commands.neutral.GuildCommandRegistrar
import es.wokis.commands.next.NextCommandHandler
import es.wokis.commands.player.PlayerCommandController
import es.wokis.commands.player.PlayerMessageCoordinators
import es.wokis.commands.player.QueueMessageViewFactory
import es.wokis.commands.queue.QueueCommandHandler
import es.wokis.commands.queue.QueuePaginationHandler
import es.wokis.commands.radio.RadioCatalogPort
import es.wokis.commands.radio.RadioCommandHandler
import es.wokis.commands.radio.RadioCountryCodeAutocompleteHandler
import es.wokis.commands.radio.RadioCountryCodesCommandHandler
import es.wokis.commands.radio.RadioCountryCodesPaginationHandler
import es.wokis.commands.radio.RadioListCommandHandler
import es.wokis.commands.radio.RadioListPaginationHandler
import es.wokis.commands.radio.RadioMessageViewFactory
import es.wokis.commands.radio.RadioPlayAutocompleteHandler
import es.wokis.commands.radio.RadioPlayCommandHandler
import es.wokis.commands.radio.RadioRandomCommandHandler
import es.wokis.commands.radio.RadioSearchCommandHandler
import es.wokis.commands.radio.RadioSearchKind
import es.wokis.commands.radio.RadioSearchPaginationHandler
import es.wokis.commands.reconnect.ReconnectCommandHandler
import es.wokis.commands.shuffle.ShuffleCommandHandler
import es.wokis.commands.skip.SkipCommandHandler
import es.wokis.commands.sound.SoundAutocompleteHandler
import es.wokis.commands.sound.SoundCommandHandler
import es.wokis.commands.sounds.SoundPaginationHandler
import es.wokis.commands.sounds.SoundsCommandHandler
import es.wokis.commands.sounds.SoundsMessageViewFactory
import es.wokis.commands.tts.TtsCommandHandler
import es.wokis.domain.locale.GetGuildLocaleUseCase
import es.wokis.domain.locale.SetGuildLocaleUseCase
import es.wokis.domain.model.GuildId
import es.wokis.jda.command.JdaAutocompleteRouter
import es.wokis.jda.command.JdaCommandRouter
import es.wokis.jda.command.JdaComponentRouter
import es.wokis.jda.command.JdaGuildCommandRegistrar
import es.wokis.jda.player.JdaDisconnectCommand
import es.wokis.jda.player.JdaNeutralPlayerCommand
import es.wokis.jda.player.JdaNextCommand
import es.wokis.jda.player.JdaPlayCommand
import es.wokis.jda.player.JdaPlayerActionsPort
import es.wokis.jda.player.JdaPlayerButtonHandler
import es.wokis.jda.player.JdaPlayerControlCommand
import es.wokis.jda.player.JdaPlayerRuntime
import es.wokis.jda.player.JdaPlayerVoiceTargetPort
import es.wokis.jda.player.JdaQueueCommand
import es.wokis.jda.player.JdaRadioCommand
import es.wokis.jda.player.JdaReconnectCommand
import es.wokis.jda.player.JdaShuffleCommand
import es.wokis.jda.player.JdaSkipCommand
import es.wokis.jda.player.JdaSoundCommand
import es.wokis.jda.player.JdaSoundsCatalogPort
import es.wokis.jda.player.JdaSoundsCommand
import es.wokis.jda.player.JdaTtsCommand
import es.wokis.jda.player.PLAYER_BUTTON_IDS
import es.wokis.jda.player.QUEUE_PAGINATION_IDS
import es.wokis.jda.player.RADIO_COUNTRY_CODES_PAGINATION_IDS
import es.wokis.jda.player.RADIO_LIST_PAGINATION_IDS
import es.wokis.jda.player.RADIO_SEARCH_COUNTRY_CODE_PAGINATION_IDS
import es.wokis.jda.player.RADIO_SEARCH_NAME_PAGINATION_IDS
import es.wokis.jda.player.SOUNDS_PAGINATION_IDS
import es.wokis.jda.radio.JdaRadioCatalogPort
import es.wokis.jda.radio.JdkRadioCatalogHttp
import es.wokis.jda.radio.radioEndpoint
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
    // Config load/validation failures are console-only by construction: the tee is installed below, not yet.
    val exitCode = try {
        val sharedConfig = loadSharedConfig()
        val runtimeConfig = JdaRuntimeConfig.fromConfig(sharedConfig)
        StagingLogTee.install(sharedConfig.logFile)
        runBootstrap(runtimeConfig)
    } catch (configurationError: JdaRuntimeConfigException) {
        LOG.error("Invalid JDA runtime configuration: {}", configurationError.message)
        EXIT_FAILURE
    }

    // main() is intentionally thin (real FS + exitProcess) with no unit-test seam; do not refactor this order.
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
    var autocompleteRouter: JdaAutocompleteRouter? = null
    var jda: JDA? = null
    var shutdownHook: Thread? = null
    var playerRuntime: JdaPlayerRuntime? = null
    var primaryFailure: Throwable? = null

    try {
        val client = buildGatewayClient(config, davePreflight, clientFactory)
        jda = client

        playerRuntime = JdaPlayerRuntime(sources = config.sources)

        val radioCatalog = JdaRadioCatalogPort(JdkRadioCatalogHttp(radioEndpoint(config.debug)))
        val commands = buildCommands(client, playerRuntime, radioCatalog)
        router = commands.router
        componentRouter = commands.componentRouter
        autocompleteRouter = commands.autocompleteRouter
        client.addEventListener(commands.router)
        client.addEventListener(commands.componentRouter)
        client.addEventListener(commands.autocompleteRouter)

        shutdownHook = Thread({ client.shutdown() }, "jda-shutdown").also {
            Runtime.getRuntime().addShutdownHook(it)
        }

        client.awaitReady()

        val joinedGuildIds = client.guilds.map { GuildId(it.idLong.toULong()) }
        registerCommands(client, commands, joinedGuildIds)

        if (joinedGuildIds.isEmpty()) {
            LOG.warn(
                "JDA ready as {} but the client is not a member of any guild; no command registered",
                client.selfUser.name,
            )
        }
        LOG.info(
            "JDA ready as {}; guild-scoped commands registered in {} guild(s); " +
                "intents=[GUILD_VOICE_STATES]",
            client.selfUser.name,
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
        autocompleteRouter = autocompleteRouter,
        shutdownHook = shutdownHook,
        playerRuntime = playerRuntime,
    )
    val cleanupReport = runBootstrapCleanupWithFallback(cleanupStepsFactory, targets)

    return finishBootstrap(primaryFailure, cleanupReport)
}

/** Upserts every guild-scoped command into each joined guild exactly once. */
private fun registerCommands(
    client: JDA,
    commands: BootstrapCommands,
    joinedGuildIds: List<GuildId>,
) {
    runBlocking {
        val registrar = JdaGuildCommandRegistrar(client)
        joinedGuildIds.forEach { guildId ->
            registerLocaleCommand(registrar, guildId, commands.localizationService)
            registerPlayerCommands(
                registrar,
                guildId,
                commands.playCommand,
                commands.controlCommand,
                commands.familyCommands,
            )
            commands.radioCommand.registerIn(registrar, guildId)
        }
    }
}

private class BootstrapCommands(
    val localeCommand: LocaleCommandHandler,
    val localizationService: GuildLocalizationService,
    val playCommand: JdaPlayCommand,
    val controlCommand: JdaPlayerControlCommand,
    val familyCommands: List<JdaNeutralPlayerCommand>,
    val radioCommand: JdaRadioCommand,
    val router: JdaCommandRouter,
    val componentRouter: JdaComponentRouter,
    val autocompleteRouter: JdaAutocompleteRouter,
)

private fun buildCommands(
    client: JDA,
    playerRuntime: JdaPlayerRuntime,
    radioCatalog: RadioCatalogPort,
): BootstrapCommands {
    val localeRepository = LocalJsonLocaleRepository(LocaleJson.json)
    val localizationService = GuildLocalizationService(GetGuildLocaleUseCase(localeRepository))
    val localeCommand = LocaleCommandHandler(
        setGuildLocaleUseCase = SetGuildLocaleUseCase(localeRepository),
        localizationService = localizationService,
    )
    val actions = JdaPlayerActionsPort(jda = client, runtime = playerRuntime)
    val playerController = PlayerCommandController(
        actions = actions,
        coordinators = playerCoordinators(client, playerRuntime),
        voiceTargets = JdaPlayerVoiceTargetPort(client),
        localizationService = localizationService,
    )
    val playCommand = JdaPlayCommand(playerController, localizationService)
    val controlCommand = JdaPlayerControlCommand(playerController, localizationService)

    val queueViewFactory = QueueMessageViewFactory(localizationService)
    val soundsCatalog = JdaSoundsCatalogPort()
    val soundsViewFactory = SoundsMessageViewFactory(localizationService)
    val radioViewFactory = RadioMessageViewFactory(localizationService)
    val radioCommand = buildRadioCommand(radioCatalog, actions, radioViewFactory, localizationService)
    val familyCommands = buildFamilyCommands(
        actions = actions,
        queueViewFactory = queueViewFactory,
        soundsCatalog = soundsCatalog,
        soundsViewFactory = soundsViewFactory,
        localizationService = localizationService,
    )

    return BootstrapCommands(
        localeCommand = localeCommand,
        localizationService = localizationService,
        playCommand = playCommand,
        controlCommand = controlCommand,
        familyCommands = familyCommands,
        radioCommand = radioCommand,
        router = JdaCommandRouter(listOf(localeCommand, playCommand, controlCommand) + familyCommands + radioCommand),
        componentRouter = buildComponentRouter(
            client = client,
            playerRuntime = playerRuntime,
            playerController = playerController,
            actions = actions,
            queueViewFactory = queueViewFactory,
            soundsCatalog = soundsCatalog,
            soundsViewFactory = soundsViewFactory,
            radioCatalog = radioCatalog,
            radioViewFactory = radioViewFactory,
        ),
        autocompleteRouter = JdaAutocompleteRouter(
            listOf(
                LocaleAutocompleteHandler(),
                SoundAutocompleteHandler(soundsCatalog),
                RadioPlayAutocompleteHandler(radioCatalog),
                RadioCountryCodeAutocompleteHandler(radioCatalog),
            ),
        ),
    )
}

private fun playerCoordinators(
    client: JDA,
    playerRuntime: JdaPlayerRuntime,
): PlayerMessageCoordinators = PlayerMessageCoordinators { guildId ->
    val rawId = guildId.value.takeIf { it <= Long.MAX_VALUE.toULong() }
    rawId?.let { client.getGuildById(it.toLong()) }?.let(playerRuntime::messagesFor)
}

private fun buildRadioCommand(
    radioCatalog: RadioCatalogPort,
    actions: JdaPlayerActionsPort,
    radioViewFactory: RadioMessageViewFactory,
    localizationService: GuildLocalizationService,
): JdaRadioCommand = JdaRadioCommand(
    handler = RadioCommandHandler(
        play = RadioPlayCommandHandler(radioCatalog, actions, localizationService),
        list = RadioListCommandHandler(radioCatalog, radioViewFactory),
        searchByName = RadioSearchCommandHandler(
            RadioSearchKind.NAME,
            radioCatalog,
            radioViewFactory,
        ),
        searchByCountryCode = RadioSearchCommandHandler(
            RadioSearchKind.COUNTRY_CODE,
            radioCatalog,
            radioViewFactory,
        ),
        random = RadioRandomCommandHandler(radioCatalog, actions, localizationService),
        countryCodes = RadioCountryCodesCommandHandler(
            radioCatalog,
            radioViewFactory,
            localizationService,
        ),
        localizationService = localizationService,
    ),
    localizationService = localizationService,
)

private fun buildFamilyCommands(
    actions: JdaPlayerActionsPort,
    queueViewFactory: QueueMessageViewFactory,
    soundsCatalog: JdaSoundsCatalogPort,
    soundsViewFactory: SoundsMessageViewFactory,
    localizationService: GuildLocalizationService,
): List<JdaNeutralPlayerCommand> = listOf(
    JdaSkipCommand(SkipCommandHandler(actions, localizationService), localizationService),
    JdaShuffleCommand(ShuffleCommandHandler(actions, localizationService), localizationService),
    JdaNextCommand(NextCommandHandler(actions, localizationService), localizationService),
    JdaQueueCommand(QueueCommandHandler(actions, localizationService, queueViewFactory), localizationService),
    JdaDisconnectCommand(DisconnectCommandHandler(actions, localizationService), localizationService),
    JdaReconnectCommand(ReconnectCommandHandler(actions, localizationService), localizationService),
    JdaSoundCommand(SoundCommandHandler(actions, soundsCatalog, localizationService), localizationService),
    JdaSoundsCommand(SoundsCommandHandler(soundsCatalog, soundsViewFactory), localizationService),
    JdaTtsCommand(TtsCommandHandler(actions, localizationService), localizationService),
)

private fun buildComponentRouter(
    client: JDA,
    playerRuntime: JdaPlayerRuntime,
    playerController: PlayerCommandController,
    actions: JdaPlayerActionsPort,
    queueViewFactory: QueueMessageViewFactory,
    soundsCatalog: JdaSoundsCatalogPort,
    soundsViewFactory: SoundsMessageViewFactory,
    radioCatalog: RadioCatalogPort,
    radioViewFactory: RadioMessageViewFactory,
): JdaComponentRouter = JdaComponentRouter(
    PLAYER_BUTTON_IDS.map { id ->
        JdaPlayerButtonHandler(
            id = id,
            jda = client,
            runtime = playerRuntime,
            controller = playerController,
        )
    } + QUEUE_PAGINATION_IDS.map { id ->
        QueuePaginationHandler(id = id, actions = actions, viewFactory = queueViewFactory)
    } + SOUNDS_PAGINATION_IDS.map { id ->
        SoundPaginationHandler(id = id, soundsCatalog = soundsCatalog, viewFactory = soundsViewFactory)
    } + RADIO_LIST_PAGINATION_IDS.map { id ->
        RadioListPaginationHandler(id = id, catalog = radioCatalog, viewFactory = radioViewFactory)
    } + RADIO_SEARCH_NAME_PAGINATION_IDS.map { id ->
        RadioSearchPaginationHandler(
            id = id,
            kind = RadioSearchKind.NAME,
            catalog = radioCatalog,
            viewFactory = radioViewFactory,
        )
    } + RADIO_SEARCH_COUNTRY_CODE_PAGINATION_IDS.map { id ->
        RadioSearchPaginationHandler(
            id = id,
            kind = RadioSearchKind.COUNTRY_CODE,
            catalog = radioCatalog,
            viewFactory = radioViewFactory,
        )
    } + RADIO_COUNTRY_CODES_PAGINATION_IDS.map { id ->
        RadioCountryCodesPaginationHandler(id = id, catalog = radioCatalog, viewFactory = radioViewFactory)
    },
)

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
    familyCommands: List<JdaNeutralPlayerCommand>,
) {
    playCommand.registerIn(registrar, guildId)
    controlCommand.registerIn(registrar, guildId)
    familyCommands.forEach { command -> command.registerIn(registrar, guildId) }
}
