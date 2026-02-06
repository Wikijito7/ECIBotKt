package services.lavaplayer.manager

import com.sedmelluq.discord.lavaplayer.container.mp3.Mp3AudioTrack
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo
import com.sedmelluq.discord.lavaplayer.track.DelegatedAudioTrack
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.ContentType
import org.apache.http.entity.StringEntity
import java.net.URI

class KokoroAudioTrack(
    trackInfo: AudioTrackInfo,
    private val sourceManager: KokoroSourceManager
) : DelegatedAudioTrack(trackInfo) {

    var baseUrl: String = ""
    var rawText: String = ""
    var voice: String = ""
    var speed: Float = 1.0f
    var langCode: String = ""

    override fun process(localExecutor: LocalAudioTrackExecutor) {
        if (baseUrl.isEmpty()) throw IllegalStateException("Base URL not set for KokoroAudioTrack")
        
        sourceManager.getHttpInterface().use { httpInterface ->
            val post = HttpPost(URI.create("$baseUrl/v1/audio/speech").normalize()).apply {
                entity = StringEntity(getBody(), ContentType.APPLICATION_JSON)
            }
            
            httpInterface.execute(post).use { response ->
                val contentLength = response.getFirstHeader("Content-Length")?.value?.toLongOrNull()
                val stream = PostAudioStream(response, contentLength)
                
                processDelegate(Mp3AudioTrack(trackInfo, stream), localExecutor)
            }
        }
    }
  
    override fun makeShallowClone(): AudioTrack = KokoroAudioTrack(trackInfo, sourceManager).apply {
        baseUrl = this@KokoroAudioTrack.baseUrl
        rawText = this@KokoroAudioTrack.rawText
        voice = this@KokoroAudioTrack.voice
        speed = this@KokoroAudioTrack.speed
        langCode = this@KokoroAudioTrack.langCode
    }
  
    override fun getSourceManager(): AudioSourceManager = sourceManager

    private fun getBody(): String {
        // Escape JSON special characters in rawText
        val escapedText = rawText
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
        
        return """
            {
                "input": "$escapedText",
                "voice": "$voice",
                "response_format": "mp3",
                "stream": true,
                "speed": $speed,
                "lang_code": "$langCode"
            }
        """.trimIndent()
    }
}