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
 * Manual composition root for the isolated JDA real-player slice.
 *
 * The JDA runtime has no Koin container, so this class wires the shared player engines by hand:
 * one Lavaplayer manager for every guild, one long-lived engine per guild, and one voice adapter
 * per guild. The root Kord adapter creates the same engine type through `:player`; only the
 * gateway presentation differs.
 *
 * **Event ownership:** [JdaGuildPlayerVoiceAdapter] is the only collector of each engine's
 * single-subscriber `PlayerEngine.events` stream. Phase 4 consumers must go through the adapter
 * rather than collecting the engine stream themselves.
 *
 * The Phase 4 staging enabler adds the minimal command surface (`/jda-play` and `/jda-player`) in
 * this module; it does not port the remaining command families, which stay Phase 4 work. The Phase
 * 2C diagnostic walkthrough still uses its own finite `AudioFrameSource` path and is mutually
 * exclusive with the real player's voice connection.
 *
 * [sources] is the same neutral source-provider configuration the root Kord adapter maps from its
 * JSON config. The isolated runtime's bootstrap now loads the shared configuration file and passes
 * the mapped providers here, so both runtimes register the same YouTube/LavaSrc/Kokoro/local/remote
 * sources. Every provider option remains injectable through this parameter.
 *
 * **Serialization scope:** each guild adapter owns its own single-coroutine dispatcher so a slow or
 * blocked connect/disconnect in one guild never stalls another. [serialDispatcherFactory] is
 * invoked once per adapter created, so the production default hands every guild a fresh
 * `Dispatchers.Default.limitedParallelism(1)` view instead of one process-wide view shared by all
 * adapters (which would serialize unrelated guilds). Inject a factory to observe or control that
 * per-guild serialization without changing connect/disconnect/close semantics.
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

    /**
     * Returns (creating on first use) the shared command/snapshot facade for [guild].
     *
     * Do **not** collect [PlayerEngine.events] from this facade: [JdaGuildPlayerVoiceAdapter] is
     * the single subscriber and event owner. Phase 4/UI callers must issue commands or read
     * [PlayerEngine.snapshot] here, or consume presentation state from the adapter.
     */
    fun engineFor(guild: Guild): PlayerEngine = synchronized(lock) {
        check(!closed.get()) { "The JDA player runtime is closed" }
        engines.computeIfAbsent(guild.idLong) { factory.value.create() }
    }

    /** Returns (creating on first use) the long-lived voice adapter for [guild]. */
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
     * Opens the guild's long-lived player voice connection to [channel].
     *
     * @return `true` when the adapter owns the connection after the call; `false` when it refused
     * to take over a voice session owned by another voice path or the runtime is closing.
     */
    suspend fun connect(guild: Guild, channel: VoiceChannel): Boolean =
        voiceAdapterFor(guild).connect(channel)

    /** Closes the guild's player voice connection without destroying its queue/engine. */
    suspend fun disconnect(guild: Guild) {
        adapters[guild.idLong]?.disconnect()
    }

    /**
     * Full runtime teardown: await every adapter's JDA cleanup, then close engines and the shared
     * manager exactly once. Cancellable.
     */
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

    /** Fast, non-blocking signal. Use [shutdown] to await JDA and engine cleanup. */
    override fun close() {
        val adaptersToClose: List<JdaGuildPlayerVoiceAdapter>
        val enginesToClose: List<PlayerEngine>
        synchronized(lock) {
            closed.set(true)
            // Keep adapter references so a later explicit shutdown() can still await the JDA
            // disconnect; close() is only the fast signal.
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
