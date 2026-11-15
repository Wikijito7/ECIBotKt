package es.wokis.services.lavaplayer

import es.wokis.player.engine.PlayerEngine
import es.wokis.player.engine.PlayerEngines
import es.wokis.player.engine.config.DeezerSourceConfig
import es.wokis.player.engine.config.KokoroSourceConfig
import es.wokis.player.engine.config.PlayerSourcesConfig
import es.wokis.player.engine.config.SpotifySourceConfig
import es.wokis.player.engine.config.TidalSourceConfig
import es.wokis.player.engine.config.YoutubeSourceConfig
import es.wokis.services.config.Config
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

private fun Config.toPlayerSourcesConfig(): PlayerSourcesConfig = PlayerSourcesConfig(
    youtube = YoutubeSourceConfig(
        enabled = youtube.enabled,
        oauth2Token = youtube.oauth2Token,
        poToken = youtube.poToken,
        visitorData = youtube.visitorData,
        remoteCipherUrl = youtube.remoteCipherUrl,
        remoteCipherPassword = youtube.remoteCipherPassword,
    ),
    deezer = DeezerSourceConfig(
        enabled = deezer.enabled,
        masterDecryptionKey = deezer.masterDecryptionKey,
        arlToken = deezer.arlToken,
    ),
    spotify = SpotifySourceConfig(
        enabled = spotify.enabled,
        clientId = spotify.clientId,
        clientSecret = spotify.clientSecret,
        customEndpoint = spotify.customEndpoint,
    ),
    tidal = TidalSourceConfig(
        enabled = tidal.enabled,
        countryCode = tidal.countryCode,
        token = tidal.token,
    ),
    kokoro = KokoroSourceConfig(
        baseUrl = kokoro.baseUrl,
        defaultVoice = kokoro.defaultVoice,
        defaultSpeed = kokoro.defaultSpeed,
        defaultLangCode = kokoro.defaultLangCode,
    ),
)
