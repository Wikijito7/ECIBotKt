package es.wokis.services.assistant

import es.wokis.services.config.ConfigService
import es.wokis.utils.Log
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import de.maxhenkel.opus4j.OpusDecoder
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WhisperService(
    private val httpClient: HttpClient,
    private val configService: ConfigService
) {
    private var decoder: OpusDecoder? = null

    suspend fun transcribePcm(pcmData: ShortArray): String? {
        val config = configService.config.whisper
        if (!config.enabled) {
            Log.warning("Whisper is not enabled in config")
            return null
        }

        return try {
            Log.info("Converting ${pcmData.size} PCM samples to WAV")

            // Convert PCM to WAV
            val wavBytes = createWavFromPcm(pcmData)
            Log.info("Created WAV: ${wavBytes.size} bytes, header: ${wavBytes.take(44).toList()}")

            Log.info("Sending to whisper: baseUrl=${config.baseUrl}")
            
            // Send to whisper
            val response: WhisperResponse = httpClient.post(config.baseUrl) {
                setBody(
                    MultiPartFormDataContent(
                        formData {
                            append(
                                key = "file",
                                value = wavBytes,
                                headers = Headers.build {
                                    append(HttpHeaders.ContentType, "audio/wav")
                                    append(HttpHeaders.ContentDisposition, "filename=\"audio.wav\"")
                                }
                            )
                            append("model", "whisper-1")
                            append("response_format", "json")
                        }
                    )
                )
            }.body()

            Log.info("Whisper response: transcript='${response.transcript}'")
            response.transcript
        } catch (e: Exception) {
            Log.error("Error transcribing audio", exception = e)
            null
        }
    }

    suspend fun transcribeAudio(audioBytes: ByteArray): String? {
        val config = configService.config.whisper
        if (!config.enabled) {
            Log.warning("Whisper is not enabled in config")
            return null
        }

        return try {
            Log.info("Decoding Opus audio: ${audioBytes.size} bytes")

            // Decode Opus to PCM
            val pcmData = decodeOpusToPcm(audioBytes)
            Log.info("Decoded to PCM: ${pcmData.size} samples")

            // Convert PCM to WAV
            val wavBytes = createWavFromPcm(pcmData)
            Log.info("Created WAV: ${wavBytes.size} bytes")

            // Send to whisper
            val response: WhisperResponse = httpClient.post(config.baseUrl) {
                setBody(
                    MultiPartFormDataContent(
                        formData {
                            append(
                                key = "file",
                                value = wavBytes,
                                headers = Headers.build {
                                    append(HttpHeaders.ContentType, "audio/wav")
                                    append(HttpHeaders.ContentDisposition, "filename=\"audio.wav\"")
                                }
                            )
                            append("model", "whisper-1")
                            append("response_format", "json")
                        }
                    )
                )
            }.body()

            response.transcript
        } catch (e: Exception) {
            Log.error("Error transcribing audio", exception = e)
            null
        }
    }

    private fun decodeOpusToPcm(opusData: ByteArray): ShortArray {
        // Create decoder if not exists
        if (decoder == null) {
            decoder = OpusDecoder(48000, 2)
            decoder?.setFrameSize(960)
        }

        return try {
            decoder?.decode(opusData) ?: shortArrayOf()
        } catch (e: Exception) {
            Log.error("Error decoding Opus", exception = e)
            shortArrayOf()
        }
    }

    private fun createWavFromPcm(pcmData: ShortArray): ByteArray {
        val sampleRate = 48000
        val numChannels = 2
        val bitsPerSample = 16
        val byteRate = sampleRate * numChannels * bitsPerSample / 8
        val blockAlign = numChannels * bitsPerSample / 8
        val dataSize = pcmData.size * 2 // Short is 2 bytes
        val wavSize = 36 + dataSize

        val wav = ByteArray(44 + dataSize)
        val buffer = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN)

        // RIFF header
        buffer.put("RIFF".toByteArray())
        buffer.putInt(wavSize)
        buffer.put("WAVE".toByteArray())

        // fmt subchunk
        buffer.put("fmt ".toByteArray())
        buffer.putInt(16)
        buffer.putShort(1) // PCM
        buffer.putShort(numChannels.toShort())
        buffer.putInt(sampleRate)
        buffer.putInt(byteRate)
        buffer.putShort(blockAlign.toShort())
        buffer.putShort(bitsPerSample.toShort())

        // data subchunk
        buffer.put("data".toByteArray())
        buffer.putInt(dataSize)

        // Convert ShortArray to ByteArray
        for (sample in pcmData) {
            buffer.putShort(sample)
        }

        return wav
    }

    fun close() {
        decoder?.close()
        decoder = null
    }
}

@Serializable
data class WhisperResponse(
    val transcript: String
)
