package es.wokis.services.lavaplayer

import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers
import dev.lavalink.youtube.YoutubeAudioSourceManager
import es.wokis.services.config.ConfigService
import es.wokis.services.config.youtubeOauth2Token
import com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeAudioSourceManager as DeprecatedYoutubeAudioSourceManager

class AudioPlayerManagerProvider(
    private val configService: ConfigService
) {

    fun createAudioPlayerManager(): AudioPlayerManager = DefaultAudioPlayerManager().apply {
        val ytSourceManager = YoutubeAudioSourceManager().apply {
            useOauth2(configService.youtubeOauth2Token, true)
        }
        this.registerSourceManager(ytSourceManager)
        AudioSourceManagers.registerLocalSource(this)
        AudioSourceManagers.registerRemoteSources(
            this,
            DeprecatedYoutubeAudioSourceManager::class.java
        )
    }
}
