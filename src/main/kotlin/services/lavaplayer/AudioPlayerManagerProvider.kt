package es.wokis.services.lavaplayer

import com.github.topi314.lavasrc.deezer.DeezerAudioSourceManager
import com.github.topi314.lavasrc.flowerytts.FloweryTTSSourceManager
import com.github.topi314.lavasrc.mirror.DefaultMirroringAudioTrackResolver
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
import com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeAudioSourceManager as DeprecatedYoutubeAudioSourceManager

private const val DEFAULT_TTS_VOICE = "4ba7bd1b-cb5f-5c3f-9e1c-9ee8be2b0bdd"

class AudioPlayerManagerProvider(
    private val configService: ConfigService
) {

    fun createAudioPlayerManager(): AudioPlayerManager = DefaultAudioPlayerManager().apply {
        val youtubeOptions: YoutubeSourceOptions = YoutubeSourceOptions()
            .apply {
                if (configService.config.youtube.remoteCipherUrl != null) {
                    setRemoteCipher(configService.config.youtube.remoteCipherUrl, configService.config.youtube.remoteCipherPassword.orEmpty(), null)
                }
            }
        val ytSourceManager = YoutubeAudioSourceManager(youtubeOptions, TvHtml5EmbeddedWithThumbnail(), WebWithThumbnail(), MusicWithThumbnail()).apply {
            setPlaylistPageCount(Integer.MAX_VALUE)
            useOauth2(configService.config.youtube.oauth2Token, true)
            Web.setPoTokenAndVisitorData(
                configService.config.youtube.poToken,
                configService.config.youtube.visitorData
            )
        }
        this.registerSourceManager(ytSourceManager)
        if (configService.config.deezer.enabled) {
            this.registerSourceManager(
                DeezerAudioSourceManager(
                    configService.config.deezer.masterDecryptionKey,
                    configService.config.deezer.arlToken
                )
            )
        }
        if (configService.config.spotify.enabled) {
            this.registerSourceManager(
                SpotifySourceManager(
                    /* clientId = */
                    configService.config.spotify.clientId,
                    /* clientSecret = */
                    configService.config.spotify.clientSecret,
                    /* countryCode = */
                    null,
                    /* audioPlayerManager = */
                    this,
                    /* mirroringAudioTrackResolver = */
                    DefaultMirroringAudioTrackResolver(null)
                ).apply {
                    configService.config.spotify.customEndpoint.takeIfNotEmpty()?.let {
                        setCustomTokenEndpoint(it)
                        setPreferAnonymousToken(false)
                    }
                }
            )
        }
        if (configService.config.tidal.enabled) {
            this.registerSourceManager(
                TidalSourceManager(
                    configService.config.tidal.countryCode,
                    { this },
                    DefaultMirroringAudioTrackResolver(null),
                    configService.config.tidal.token
                )
            )
        }
        this.registerSourceManager(
            FloweryTTSSourceManager(DEFAULT_TTS_VOICE).apply {
                setSpeed(1.1f)
            }
        )
        AudioSourceManagers.registerLocalSource(this)
        AudioSourceManagers.registerRemoteSources(
            this,
            DeprecatedYoutubeAudioSourceManager::class.java
        )
    }
}
