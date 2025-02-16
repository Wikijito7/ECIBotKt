package es.wokis.services.lavaplayer

import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers
import dev.lavalink.youtube.YoutubeAudioSourceManager
import com.github.topi314.lavasrc.deezer.DeezerAudioSourceManager
import es.wokis.services.config.ConfigService
import com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeAudioSourceManager as DeprecatedYoutubeAudioSourceManager

class AudioPlayerManagerProvider(
    private val configService: ConfigService
) {

    fun createAudioPlayerManager(): AudioPlayerManager = DefaultAudioPlayerManager().apply {
        val ytSourceManager = YoutubeAudioSourceManager().apply {
            useOauth2(configService.config.youtube.oauth2Token, true)
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
        AudioSourceManagers.registerLocalSource(this)
        AudioSourceManagers.registerRemoteSources(
            this,
            DeprecatedYoutubeAudioSourceManager::class.java
        )
    }
}
