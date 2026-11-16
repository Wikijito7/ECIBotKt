package es.wokis.player.engine

import es.wokis.player.engine.config.DeezerSourceConfig
import es.wokis.player.engine.config.KokoroSourceConfig
import es.wokis.player.engine.config.PlayerSourcesConfig
import es.wokis.player.engine.config.SpotifySourceConfig
import es.wokis.player.engine.config.TidalSourceConfig
import es.wokis.player.engine.config.YoutubeSourceConfig
import es.wokis.player.engine.source.LavaAudioPlayerManagers
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlayerSourceConfigurationTest {

    @Test
    fun `Given the default config When the source graph is built Then local and remote are available without optional providers`() {
        val manager = LavaAudioPlayerManagers.create(PlayerSourcesConfig())

        val sourceNames = manager.sourceManagers.map { it.sourceName }

        try {
            assertTrue("local" in sourceNames)
            assertTrue("http" in sourceNames)
            assertFalse("youtube" in sourceNames)
            assertFalse("deezer" in sourceNames)
            assertFalse("spotify" in sourceNames)
            assertFalse("tidal" in sourceNames)
            assertFalse("kokoro" in sourceNames)
        } finally {
            manager.shutdown()
        }
    }

    @Test
    fun `Given every optional provider enabled When the source graph is built Then each provider is registered`() {
        val manager = LavaAudioPlayerManagers.create(
            PlayerSourcesConfig(
                youtube = YoutubeSourceConfig(enabled = true),
                deezer = DeezerSourceConfig(enabled = true, masterDecryptionKey = "key", arlToken = "token"),
                spotify = SpotifySourceConfig(enabled = true, clientId = "id", clientSecret = "secret"),
                tidal = TidalSourceConfig(enabled = true, countryCode = "ES", token = "token"),
                kokoro = KokoroSourceConfig(baseUrl = "http://localhost:8880"),
            ),
        )

        val sourceNames = manager.sourceManagers.map { it.sourceName }

        try {
            assertTrue("youtube" in sourceNames)
            assertTrue("deezer" in sourceNames)
            assertTrue("spotify" in sourceNames)
            assertTrue("tidal" in sourceNames)
            assertTrue("kokoro" in sourceNames)
        } finally {
            manager.shutdown()
        }
    }

    @Test
    fun `Given a default config When an engine is created Then it owns an empty engine that can close`() {
        val engine = PlayerEngines.fromConfig(PlayerSourcesConfig())

        try {
            assertTrue(engine.isQueueEmpty())
            assertTrue(engine.snapshot().queue.isEmpty())
        } finally {
            engine.close()
        }
    }
}
