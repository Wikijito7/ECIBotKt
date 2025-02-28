package es.wokis.services.lavaplayer

import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers
import dev.lavalink.youtube.YoutubeAudioSourceManager
import com.github.topi314.lavasrc.deezer.DeezerAudioSourceManager
import com.github.topi314.lavasrc.flowerytts.FloweryTTSSourceManager
import com.github.topi314.lavasrc.mirror.DefaultMirroringAudioTrackResolver
import com.github.topi314.lavasrc.spotify.SpotifySourceManager
import dev.lavalink.youtube.clients.Web
import es.wokis.services.config.ConfigService
import com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeAudioSourceManager as DeprecatedYoutubeAudioSourceManager

private const val DEFAULT_TTS_VOICE = "4ba7bd1b-cb5f-5c3f-9e1c-9ee8be2b0bdd"

class AudioPlayerManagerProvider(
    private val configService: ConfigService
) {

    fun createAudioPlayerManager(): AudioPlayerManager = DefaultAudioPlayerManager().apply {
        val ytSourceManager = YoutubeAudioSourceManager().apply {
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
                    configService.config.spotify.clientId,
                    configService.config.spotify.clientSecret,
                    null,
                    this,
                    DefaultMirroringAudioTrackResolver(null)
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
