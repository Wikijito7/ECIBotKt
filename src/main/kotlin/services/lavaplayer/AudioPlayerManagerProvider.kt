package es.wokis.services.lavaplayer

import es.wokis.player.engine.PlayerEngine
import es.wokis.player.engine.PlayerEngines
import es.wokis.player.engine.config.toPlayerSourcesConfig
import es.wokis.services.config.ConfigService
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Kord-side adapter over the shared source-provider configuration.
 *
 * The root application does not register Lavaplayer sources itself anymore: it maps its existing
 * JSON `Config` sections onto the neutral [PlayerSourcesConfig], and the shared `:player` module
 * builds the same YouTube/LavaSrc/Kokoro/local/remote graph it always used.
 *
 * One provider (one Koin singleton) owns one Lavaplayer manager for all guilds, avoiding a thread
 * pool per guild. Per-guild [PlayerEngine]s share that manager and are closed independently; the
 * manager itself is shut down once by [close].
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
