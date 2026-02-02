package es.wokis.services.lavaplayer

import com.github.topi314.lavasrc.deezer.DeezerAudioSourceManager
import com.github.topi314.lavasrc.flowerytts.FloweryTTSSourceManager
import com.github.topi314.lavasrc.mirror.DefaultMirroringAudioTrackResolver
import com.github.topi314.lavasrc.mirror.MirroringAudioSourceManager
import com.github.topi314.lavasrc.spotify.SpotifySourceManager
import com.github.topi314.lavasrc.tidal.TidalSourceManager
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers
import dev.lavalink.youtube.YoutubeAudioSourceManager
import dev.lavalink.youtube.YoutubeSourceOptions
import dev.lavalink.youtube.clients.MusicWithThumbnail
import dev.lavalink.youtube.clients.TvHtml5EmbeddedWithThumbnail
import dev.lavalink.youtube.clients.Web
import dev.lavalink.youtube.clients.WebWithThumbnail
import es.wokis.services.config.ConfigService
import es.wokis.utils.takeIfNotEmpty

private const val DEFAULT_TTS_VOICE = "4ba7bd1b-cb5f-5c3f-9e1c-9ee8be2b0bdd"

class AudioPlayerManagerProvider(
    private val configService: ConfigService
) {

    fun createAudioPlayerManager(): AudioPlayerManager = DefaultAudioPlayerManager().apply {
        val config = configService.config
        val trackResolverProviders = buildList {
            if (config.deezer.enabled) {
                add("dzisrc:" + MirroringAudioSourceManager.ISRC_PATTERN)
                add("dzsearch:" + MirroringAudioSourceManager.QUERY_PATTERN)
            }
            if (config.youtube.enabled) {
                add("ytsearch:\"" + MirroringAudioSourceManager.ISRC_PATTERN + "\"")
                add("ytsearch:" + MirroringAudioSourceManager.QUERY_PATTERN)
            }
        }.toTypedArray()
        val mirroringAudioTrackResolver = DefaultMirroringAudioTrackResolver(trackResolverProviders)
        if (config.youtube.enabled) {
            val youtubeOptions = YoutubeSourceOptions().apply {
                if (config.youtube.remoteCipherUrl != null) {
                    setRemoteCipher(
                        config.youtube.remoteCipherUrl,
                        config.youtube.remoteCipherPassword.orEmpty(),
                        null
                    )
                }
            }
            this.registerSourceManager(
                YoutubeAudioSourceManager(youtubeOptions, TvHtml5EmbeddedWithThumbnail(), WebWithThumbnail(), MusicWithThumbnail()).apply {
                    setPlaylistPageCount(Integer.MAX_VALUE)
                    useOauth2(config.youtube.oauth2Token, true)
                    Web.setPoTokenAndVisitorData(
                        config.youtube.poToken,
                        config.youtube.visitorData
                    )
                }
            )
        }
        if (config.deezer.enabled) {
            this.registerSourceManager(
                DeezerAudioSourceManager(
                    config.deezer.masterDecryptionKey,
                    config.deezer.arlToken
                )
            )
        }
        if (config.spotify.enabled) {
            this.registerSourceManager(
                SpotifySourceManager(
                    config.spotify.clientId,
                    config.spotify.clientSecret,
                    null,
                    this,
                    mirroringAudioTrackResolver
                ).apply {
                    config.spotify.customEndpoint.takeIfNotEmpty()?.let {
                        setCustomTokenEndpoint(it)
                        setPreferAnonymousToken(false)
                    }
                }
            )
        }
        if (config.tidal.enabled) {
            this.registerSourceManager(
                TidalSourceManager(
                    config.tidal.countryCode,
                    { this },
                    mirroringAudioTrackResolver,
                    config.tidal.token
                )
            )
        }
        this.registerSourceManager(
            FloweryTTSSourceManager(DEFAULT_TTS_VOICE).apply {
                setSpeed(1.1f)
            }
        )
        AudioSourceManagers.registerLocalSource(this)
    }
}
