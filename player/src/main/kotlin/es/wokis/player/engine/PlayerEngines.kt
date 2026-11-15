package es.wokis.player.engine

import es.wokis.player.engine.config.PlayerSourcesConfig
import es.wokis.player.engine.source.LavaAudioPlayerManagers
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Construction entry point for the shared real-player engine.
 *
 * Both the root Kord adapter and the isolated JDA runtime create their engines through this
 * object, so source registration and player behaviour stay identical.
 *
 * Prefer [factory] when a runtime owns many guilds: one Lavaplayer manager (and its thread pools)
 * then owns many `AudioPlayer`s instead of one manager per guild. [fromConfig] remains the
 * convenience path for a single self-owned engine.
 */
object PlayerEngines {

    /** Creates one engine with its own source-manager graph; closing it shuts that manager down. */
    fun fromConfig(config: PlayerSourcesConfig = PlayerSourcesConfig()): PlayerEngine =
        LavaPlayerEngine(
            manager = LavaAudioPlayerManagers.create(config),
            ownsManager = true,
        )

    /**
     * Creates a shared manager/factory that can produce many per-guild engines.
     *
     * The caller must close the factory exactly once after closing every engine it created.
     */
    fun factory(config: PlayerSourcesConfig = PlayerSourcesConfig()): PlayerEngineFactory =
        PlayerEngineFactory(LavaAudioPlayerManagers.create(config))
}

/**
 * Owns one Lavaplayer manager and creates gateway-neutral engines that share it.
 *
 * The factory is intentionally the only owner of the manager: per-guild engines are created with
 * `ownsManager = false`, so closing a guild engine can never shut down another guild's player.
 * [close] is idempotent and shuts the manager down at most once.
 */
class PlayerEngineFactory internal constructor(
    private val manager: com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager,
) : AutoCloseable {

    private val closed = AtomicBoolean(false)

    /** Creates a new engine backed by this factory's shared manager. */
    fun create(): PlayerEngine {
        check(!closed.get()) { "The player engine factory is closed" }
        return LavaPlayerEngine(manager, ownsManager = false)
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        manager.shutdown()
    }
}
