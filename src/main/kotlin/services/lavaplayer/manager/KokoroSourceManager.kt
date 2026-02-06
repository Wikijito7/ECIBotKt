package services.lavaplayer.manager

import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools
import com.sedmelluq.discord.lavaplayer.tools.io.HttpConfigurable
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterfaceManager
import com.sedmelluq.discord.lavaplayer.track.AudioItem
import com.sedmelluq.discord.lavaplayer.track.AudioReference
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo
import org.apache.http.client.config.RequestConfig
import org.apache.http.impl.client.HttpClientBuilder
import java.io.DataInput
import java.io.DataOutput
import java.net.URI
import java.net.URLDecoder
import java.util.function.Consumer
import java.util.function.Function
import kotlin.text.Charsets.UTF_8

class KokoroSourceManager : AudioSourceManager, HttpConfigurable {
    private val httpInterfaceManager: HttpInterfaceManager = HttpClientTools.createDefaultThreadLocalManager()

    var baseUrl: String = ""
    var defaultVoice: String = ""
    var defaultSpeed: Float = 1.0f
    var defaultLangCode: String = ""

    companion object {
        private const val DEFAULT_VOICE = "em_santa"
        private const val DEFAULT_LANG_CODE = "e"
    }

    init {
        // Configure extended timeouts for TTS generation
        // Long text can take 30+ seconds to generate
        configureRequests { config ->
            RequestConfig.copy(config)
                .setConnectTimeout(30000)      // 30 seconds to establish connection
                .setSocketTimeout(120000)      // 2 minutes for data transfer (long TTS generation)
                .setConnectionRequestTimeout(30000)  // 30 seconds to get connection from pool
                .build()
        }
    }

    override fun getSourceName(): String = "kokoro"

    override fun loadItem(manager: AudioPlayerManager, reference: AudioReference): AudioItem? {
        if (reference.identifier.startsWith("kokoro://")) {
            // Expect format: kokoro://?text=URL_ENCODED_TEXT&voice=VOICE&speed=SPEED&lang=LANG
            val uri = URI(reference.identifier)
            val query = uri.getQuery()

            val bodyRaw = query?.substringAfter("text=")?.let {
                URLDecoder.decode(it.substringBefore("&"), UTF_8)
            } ?: throw IllegalArgumentException("Missing ?text= in kokoro URL")

            // Parse optional parameters
            val voice = query.parseQueryParam("voice") ?: defaultVoice.takeIf { it.isNotEmpty() } ?: DEFAULT_VOICE
            val speed = query.parseQueryParam("speed")?.toFloatOrNull() ?: defaultSpeed
            val langCode = query.parseQueryParam("lang") ?: defaultLangCode.takeIf { it.isNotEmpty() } ?: DEFAULT_LANG_CODE

            return KokoroAudioTrack(buildTrackInfo(reference, bodyRaw), this).apply {
                baseUrl = this@KokoroSourceManager.baseUrl
                rawText = bodyRaw
                this.voice = voice
                this.speed = speed
                this.langCode = langCode
            }
        }
        return null
    }

    private fun String?.parseQueryParam(param: String): String? {
        if (this == null) return null
        val regex = "(^|&)${param}=([^&]*)".toRegex()
        val match = regex.find(this)
        return match?.groupValues?.get(2)?.let { URLDecoder.decode(it, UTF_8) }
    }

    override fun isTrackEncodable(track: AudioTrack): Boolean = true

    override fun encodeTrack(track: AudioTrack, output: DataOutput) {}

    override fun decodeTrack(trackInfo: AudioTrackInfo, input: DataInput): AudioTrack =
        KokoroAudioTrack(trackInfo, this)

    override fun shutdown() {
        httpInterfaceManager.close()
    }

    override fun configureRequests(configurator: Function<RequestConfig, RequestConfig>) {
        httpInterfaceManager.configureRequests(configurator)
    }

    override fun configureBuilder(configurator: Consumer<HttpClientBuilder>) {
        httpInterfaceManager.configureBuilder(configurator)
    }

    fun getHttpInterface() = httpInterfaceManager.getInterface()

    private fun buildTrackInfo(reference: AudioReference, rawText: String): AudioTrackInfo {
        // Generate title: "TTS Message: first 30 chars…"
        val title = if (rawText.length > 30) {
            "TTS Message: ${rawText.take(30)}…"
        } else {
            "TTS Message: $rawText"
        }

        return AudioTrackInfo(
            /* title = */ title,
            /* author = */ "Kokoro",
            /* length = */ Long.MAX_VALUE,
            /* identifier = */ reference.identifier,
            /* isStream = */ true,
            /* uri = */ reference.identifier,
            /* artworkUrl = */ null,
            /* isrc = */ null
        )
    }
}