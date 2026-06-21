package es.wokis.services.ask

import es.wokis.services.config.ConfigService
import es.wokis.services.lavaplayer.GuildLavaPlayerService
import es.wokis.services.tts.TTSService

class AskService(
    private val aiProvider: AIProvider,
    private val ttsService: TTSService,
    private val configService: ConfigService
) {
    private val config get() = configService.config.ask

    suspend fun ask(prompt: String): String {
        val model = config.model
        return aiProvider.ask(prompt, model)
    }

    suspend fun askAndPlayTTS(prompt: String, guildLavaPlayerService: GuildLavaPlayerService): String {
        val answer = ask(prompt)
        ttsService.loadAndPlayMessage(guildLavaPlayerService, answer)
        return answer
    }
}
