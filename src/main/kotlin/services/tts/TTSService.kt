package es.wokis.services.tts

import es.wokis.domain.GetFloweryVoicesUseCase
import es.wokis.services.lavaplayer.GuildLavaPlayerService
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

private const val FLOWERY_MESSAGE_MAX_LENGTH = 2048
private const val URL_ENCODED_SPACE = "+"
private const val SPACE_UTF_8 = "%20"
private const val VOICE_ARGUMENT = "?voice=%s"
private const val FLOWERY_TTS = "ftts://"

class TTSService(
    private val getFloweryVoicesUseCase: GetFloweryVoicesUseCase
) {

    private var voices: List<String> = emptyList()

    suspend fun loadAndPlayMessage(guildLavaPlayerService: GuildLavaPlayerService, message: String) {
        val randomVoice = getRandomVoice()
        val messages = getMessageChunked(message).map { chunkedMessage ->
            FLOWERY_TTS + URLEncoder
                .encode(chunkedMessage, StandardCharsets.UTF_8.toString())
                .replace(URL_ENCODED_SPACE, SPACE_UTF_8)
                .plus(randomVoice?.let { VOICE_ARGUMENT.format(it) }.orEmpty())
        }
        guildLavaPlayerService.loadAndPlayTts(messages)
    }

    private fun getMessageChunked(message: String): List<String> {
        if (message.length <= FLOWERY_MESSAGE_MAX_LENGTH) return listOf(message)

        val messageChuckedByNewLine = message.split("\n")
        if (messageChuckedByNewLine.areAllStringsCorrectlyChunked()) return messageChuckedByNewLine

        val messageChuckedByDot = messageChuckedByNewLine.chunkMessages(".")
        if (messageChuckedByDot.areAllStringsCorrectlyChunked()) return messageChuckedByDot

        val messageChuckedByComma = messageChuckedByDot.chunkMessages(",")
        if (messageChuckedByComma.areAllStringsCorrectlyChunked()) return messageChuckedByComma

        return messageChuckedByComma.chunkMessages(" ")
    }

    private fun List<String>.chunkMessages(separator: String) =
        map { chunk ->
            chunk.takeIf { it.length > FLOWERY_MESSAGE_MAX_LENGTH }?.split(separator) ?: listOf(chunk)
        }.flatten()

    private fun List<String>.areAllStringsCorrectlyChunked(): Boolean = all { it.length <= FLOWERY_MESSAGE_MAX_LENGTH }

    private suspend fun getRandomVoice() =
        (voices.takeIf { it.isNotEmpty() } ?: getFloweryVoicesUseCase().also { voices = it }).randomOrNull()
}
