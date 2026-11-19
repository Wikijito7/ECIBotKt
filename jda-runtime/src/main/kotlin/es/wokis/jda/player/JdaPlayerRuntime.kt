package es.wokis.jda.player

import es.wokis.commands.player.PlayerMessageCoordinator
import es.wokis.domain.locale.GetGuildLocaleUseCase
import es.wokis.domain.model.GuildId
import es.wokis.localization.GuildLocalizationService
import es.wokis.player.engine.PlayerEngine
import es.wokis.player.engine.PlayerEngineFactory
import es.wokis.player.engine.PlayerEngines
import es.wokis.player.engine.config.PlayerSourcesConfig
import es.wokis.player.message.PlayerMessageViewFactory
import es.wokis.repositories.locale.LocalJsonLocaleRepository
import es.wokis.repositories.locale.LocaleJson
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Manual composition root for the isolated JDA player: one lazily created [PlayerEngine], one
 * long-lived [JdaGuildPlayerVoiceAdapter] and one [PlayerMessageCoordinator] per guild.
 */
internal class JdaPlayerRuntime(
    private val sources: PlayerSourcesConfig = PlayerSourcesConfig(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val serialDispatcherFactory: () -> CoroutineDispatcher = {
        Dispatchers.Default.limitedParallelism(1)
    },
    private val localizationService: GuildLocalizationService = GuildLocalizationService(
        getGuildLocaleUseCase = GetGuildLocaleUseCase(LocalJsonLocaleRepository(LocaleJson.json)),
    ),
) : AutoCloseable {

    private val closed = AtomicBoolean(false)
    private val shutdownComplete = AtomicBoolean(false)
    private val lock = Any()
    private val factory: Lazy<PlayerEngineFactory> = lazy { PlayerEngines.factory(sources) }
    private val engines = ConcurrentHashMap<Long, PlayerEngine>()
    private val adapters = ConcurrentHashMap<Long, JdaGuildPlayerVoiceAdapter>()
    private val coordinators = ConcurrentHashMap<Long, PlayerMessageCoordinator>()
    private val viewFactory: PlayerMessageViewFactory by lazy { PlayerMessageViewFactory(localizationService) }

    fun engineFor(guild: Guild): PlayerEngine = synchronized(lock) {
        check(!closed.get()) { "The JDA player runtime is closed" }
        engines.computeIfAbsent(guild.idLong) { factory.value.create() }
    }

    fun voiceAdapterFor(guild: Guild): JdaGuildPlayerVoiceAdapter = synchronized(lock) {
        check(!closed.get()) { "The JDA player runtime is closed" }
        adapters.computeIfAbsent(guild.idLong) {
            JdaGuildPlayerVoiceAdapter(
                guild = guild,
                engine = engineFor(guild),
                scope = scope,
                serialDispatcher = serialDispatcherFactory(),
            )
        }
    }

    /**
     * One message coordinator per guild; it collects the adapter's event fan-out and owns `#player`.
     */
    fun messagesFor(guild: Guild): PlayerMessageCoordinator = synchronized(lock) {
        check(!closed.get()) { "The JDA player runtime is closed" }
        coordinators.computeIfAbsent(guild.idLong) {
            val guildEngine = engineFor(guild)
            PlayerMessageCoordinator(
                initialGuildId = GuildId(guild.idLong.toULong()),
                guildName = { guild.name },
                engine = guildEngine,
                eventsPort = JdaPlayerEventsPort(guild = guild, adapter = voiceAdapterFor(guild)),
                localizationService = localizationService,
                channelPort = JdaPlayerChannelPort(guild),
                messagePort = JdaPlayerMessagePort(guild.jda),
                voiceTargets = JdaPlayerVoiceTargetPort(guild.jda),
                viewFactory = viewFactory,
                scope = scope,
                onLeave = {
                    guildEngine.stop()
                    disconnect(guild)
                },
            )
        }
    }

    suspend fun connect(guild: Guild, channel: VoiceChannel): Boolean =
        voiceAdapterFor(guild).connect(channel)

    suspend fun reconnect(guild: Guild, channel: VoiceChannel): JdaPlayerReconnectResult =
        voiceAdapterFor(guild).reconnect(channel)

    suspend fun disconnect(guild: Guild) {
        adapters[guild.idLong]?.disconnect()
    }

    /** Full teardown: awaits adapters, then closes engines and the manager exactly once. */
    suspend fun shutdown() {
        val adaptersToClose: List<JdaGuildPlayerVoiceAdapter>
        val enginesToClose: List<PlayerEngine>
        val coordinatorsToClose: List<PlayerMessageCoordinator>
        synchronized(lock) {
            if (!shutdownComplete.compareAndSet(false, true)) return
            closed.set(true)
            adaptersToClose = adapters.values.toList().also { adapters.clear() }
            enginesToClose = engines.values.toList().also { engines.clear() }
            coordinatorsToClose = coordinators.values.toList().also { coordinators.clear() }
        }
        coordinatorsToClose.forEach { it.close() }
        val adapterFailure = captureFailures(adaptersToClose) { it.shutdown() }
        val engineFailure = captureFailures(enginesToClose) { it.close() }
        val factoryFailure = try {
            if (factory.isInitialized()) {
                factory.value.close()
            }
            null
        } catch (error: Throwable) {
            error
        } finally {
            scope.cancel()
        }
        val failures = listOfNotNull(adapterFailure, engineFailure, factoryFailure)
        if (failures.isNotEmpty()) {
            val first = failures.first()
            failures.drop(1).forEach(first::addSuppressed)
            throw first
        }
    }

    /** Fast signal; use [shutdown] to await JDA and engine cleanup. */
    override fun close() {
        val adaptersToClose: List<JdaGuildPlayerVoiceAdapter>
        val enginesToClose: List<PlayerEngine>
        val coordinatorsToClose: List<PlayerMessageCoordinator>
        synchronized(lock) {
            closed.set(true)
            // Adapter references stay so a later shutdown() can still await the JDA disconnect.
            adaptersToClose = adapters.values.toList()
            enginesToClose = engines.values.toList().also { engines.clear() }
            coordinatorsToClose = coordinators.values.toList().also { coordinators.clear() }
        }
        coordinatorsToClose.forEach { it.close() }
        adaptersToClose.forEach { it.close() }
        enginesToClose.forEach { it.close() }
        if (factory.isInitialized()) {
            factory.value.close()
        }
        scope.cancel()
    }
}

/** Closes [targets] in order, returning the first failure with the later ones suppressed. */
private suspend fun <T> captureFailures(targets: List<T>, close: suspend (T) -> Unit): Throwable? {
    var failure: Throwable? = null
    targets.forEach { target ->
        try {
            close(target)
        } catch (error: Throwable) {
            val current = failure
            if (current == null) {
                failure = error
            } else {
                current.addSuppressed(error)
            }
        }
    }
    return failure
}
