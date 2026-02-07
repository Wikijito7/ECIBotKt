package es.wokis.services.tts

import es.wokis.services.lavaplayer.GuildLavaPlayerService
import es.wokis.utils.asEncodedUrl

private const val VOICE_ARGUMENT = "?text="
private const val KOKORO_TTS = "kokoro://"

class TTSService {

    suspend fun loadAndPlayMessage(guildLavaPlayerService: GuildLavaPlayerService, message: String) {
        val ttsFormatted = "$KOKORO_TTS$VOICE_ARGUMENT${message.asEncodedUrl()}"
        guildLavaPlayerService.loadAndPlayTts(ttsFormatted)
    }
}
