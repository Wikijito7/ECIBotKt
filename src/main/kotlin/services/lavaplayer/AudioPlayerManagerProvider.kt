package es.wokis.services.lavaplayer

import es.wokis.player.engine.PlayerEngine
import es.wokis.player.engine.PlayerEngines
import es.wokis.player.engine.config.toPlayerSourcesConfig
import es.wokis.services.config.ConfigService
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Kord-side adapter over the shared source-provider configuration.
 *
 * Maps the root `Config` onto the neutral `PlayerSourcesConfig`; the shared `:player` module builds
 * the source graph. One Koin singleton owns one Lavaplayer manager for all guilds, avoiding a
 * thread pool per guild: per-guild [PlayerEngine]s share it and close independently, and [close]
 * shuts the manager down once.
 */
class AudioPlayerManagerProvider(
    private val configService: ConfigService,
) : AutoCloseable {

    private val closed = AtomicBoolean(false)
    private val lock = Any()

    private val factory = lazy {
        PlayerEngines.factory(configService.config.toPlayerSourcesConfig())
    }

    fun createPlayerEngine(): PlayerEngine = synchronized(lock) {
        check(!closed.get()) { "The player engine provider is closed" }
        factory.value.create()
    }

    override fun close() {
        synchronized(lock) {
            if (!closed.compareAndSet(false, true)) return
            if (factory.isInitialized()) {
                factory.value.close()
            }
        }
    }
}
