package es.wokis.jda.player

import es.wokis.player.engine.PlayerEngine
import es.wokis.player.engine.PlayerEngineFactory
import es.wokis.player.engine.PlayerEngines
import es.wokis.player.engine.config.PlayerSourcesConfig
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
 * Manual composition root for the isolated JDA player: one lazily created [PlayerEngine] and one
 * long-lived [JdaGuildPlayerVoiceAdapter] per guild.
 */
internal class JdaPlayerRuntime(
    private val sources: PlayerSourcesConfig = PlayerSourcesConfig(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val serialDispatcherFactory: () -> CoroutineDispatcher = {
        Dispatchers.Default.limitedParallelism(1)
    },
) : AutoCloseable {

    private val closed = AtomicBoolean(false)
    private val shutdownComplete = AtomicBoolean(false)
    private val lock = Any()
    private val factory: Lazy<PlayerEngineFactory> = lazy { PlayerEngines.factory(sources) }
    private val engines = ConcurrentHashMap<Long, PlayerEngine>()
    private val adapters = ConcurrentHashMap<Long, JdaGuildPlayerVoiceAdapter>()

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
        synchronized(lock) {
            if (!shutdownComplete.compareAndSet(false, true)) return
            closed.set(true)
            adaptersToClose = adapters.values.toList().also { adapters.clear() }
            enginesToClose = engines.values.toList().also { engines.clear() }
        }
        var failure: Throwable? = null
        adaptersToClose.forEach { adapter ->
            try {
                adapter.shutdown()
            } catch (error: Throwable) {
                if (failure == null) failure = error else failure?.addSuppressed(error)
            }
        }
        enginesToClose.forEach { engine ->
            try {
                engine.close()
            } catch (error: Throwable) {
                if (failure == null) failure = error else failure?.addSuppressed(error)
            }
        }
        try {
            if (factory.isInitialized()) {
                factory.value.close()
            }
        } catch (error: Throwable) {
            if (failure == null) failure = error else failure?.addSuppressed(error)
        } finally {
            scope.cancel()
        }
        failure?.let { throw it }
    }

    /** Fast signal; use [shutdown] to await JDA and engine cleanup. */
    override fun close() {
        val adaptersToClose: List<JdaGuildPlayerVoiceAdapter>
        val enginesToClose: List<PlayerEngine>
        synchronized(lock) {
            closed.set(true)
            // Adapter references stay so a later shutdown() can still await the JDA disconnect.
            adaptersToClose = adapters.values.toList()
            enginesToClose = engines.values.toList().also { engines.clear() }
        }
        adaptersToClose.forEach { it.close() }
        enginesToClose.forEach { it.close() }
        if (factory.isInitialized()) {
            factory.value.close()
        }
        scope.cancel()
    }
}
