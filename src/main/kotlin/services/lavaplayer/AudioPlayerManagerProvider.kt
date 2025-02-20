package es.wokis.services.lavaplayer

import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers
import dev.lavalink.youtube.YoutubeAudioSourceManager
import com.github.topi314.lavasrc.deezer.DeezerAudioSourceManager
import com.github.topi314.lavasrc.mirror.DefaultMirroringAudioTrackResolver
import com.github.topi314.lavasrc.spotify.SpotifySourceManager
import dev.lavalink.youtube.clients.Web
import es.wokis.services.config.ConfigService
import com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeAudioSourceManager as DeprecatedYoutubeAudioSourceManager

class AudioPlayerManagerProvider(
    private val configService: ConfigService
) {

    fun createAudioPlayerManager(): AudioPlayerManager = DefaultAudioPlayerManager().apply {
        val ytSourceManager = YoutubeAudioSourceManager().apply {
            useOauth2(configService.config.youtube.oauth2Token, true)
            Web.setPoTokenAndVisitorData(
                configService.config.youtube.poToken,
                configService.config.youtube.visitorData,

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
        AudioSourceManagers.registerLocalSource(this)
        AudioSourceManagers.registerRemoteSources(
            this,
            DeprecatedYoutubeAudioSourceManager::class.java
        )
    }
}
