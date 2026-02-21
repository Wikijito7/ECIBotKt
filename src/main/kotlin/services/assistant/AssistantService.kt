@file:OptIn(KordVoice::class)

package es.wokis.services.assistant

import dev.kord.common.Locale
import dev.kord.common.annotation.KordVoice
import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.core.behavior.channel.BaseVoiceChannelBehavior
import dev.kord.core.behavior.channel.connect
import dev.kord.core.entity.channel.MessageChannel
import dev.kord.voice.AudioFrame
import dev.kord.voice.VoiceConnection
import es.wokis.dispatchers.AppDispatchers
import es.wokis.localization.LocalizationKeys
import es.wokis.services.config.ConfigService
import es.wokis.services.localization.LocalizationService
import es.wokis.services.lavaplayer.GuildLavaPlayerService
import es.wokis.services.tts.TTSService
import es.wokis.utils.Log
import es.wokis.utils.createCoroutineScope
import es.wokis.utils.getLocale
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import org.koin.core.component.KoinComponent
import de.maxhenkel.opus4j.OpusDecoder

private const val TAG = "AssistantService"

private const val SILENCE_DURATION_MS = 1500L
private const val MAX_RECORDING_DURATION_MS = 60000L
private const val SILENCE_CHECK_INTERVAL_MS = 100L

@OptIn(KordVoice::class)
class AssistantService(
    private val appDispatchers: AppDispatchers,
    private val configService: ConfigService,
    private val whisperService: WhisperService,
    private val ollamaService: OllamaService,
    private val ttsService: TTSService,
    private val localizationService: LocalizationService,
    private val kord: Kord,
    private val textChannel: MessageChannel,
    private val voiceChannel: BaseVoiceChannelBehavior,
    private val guildId: Snowflake,
    private val guildLavaPlayerService: GuildLavaPlayerService
) : KoinComponent {
    private val coroutineScope = createCoroutineScope(TAG, appDispatchers)
    private var voiceConnection: VoiceConnection? = null
    private var isRecording = false
    private var isProcessing = false
    private var discordLocale: Locale? = null
    private var opusDecoder: OpusDecoder? = null

    // Store decoded PCM samples instead of Opus bytes
    private val pcmData = mutableListOf<Short>()
    private var hasReceivedAudio = false

    // Conversation history
    private val conversationHistory = mutableListOf<ConversationMessage>()

    suspend fun startAssistant() {
        Log.info("startAssistant called. isProcessing: $isProcessing")
        if (isProcessing) {
            Log.warning("Assistant is already processing")
            return
        }

        isProcessing = true
        discordLocale = voiceChannel.getLocale()
        Log.info("Locale: $discordLocale")

        try {
            Log.info("Connecting to voice channel...")
            connectToVoiceChannel()
            Log.info("Starting recording...")
            startRecording()

        } catch (e: Exception) {
            Log.error("Error starting assistant", exception = e)
            cleanup()
            textChannel.createMessage(
                localizationService.getStringFormat(
                    key = LocalizationKeys.ASSISTANT_ERROR,
                    guildId = guildId,
                    discordLocale = discordLocale,
                    arguments = arrayOf(e.message ?: "Unknown error")
                )
            )
        }
    }

    private suspend fun connectToVoiceChannel() {
        Log.info("Connecting to voice channel with receiveVoice=true")
        voiceConnection = voiceChannel.connect {
            receiveVoice = true
            selfDeaf = false
            selfMute = false
        }
        Log.info("Connected to voice channel. VoiceConnection: ${voiceConnection}, Streams: ${voiceConnection?.streams}")
    }

    private fun startRecording() {
        Log.info("Starting recording...")
        isRecording = true
        hasReceivedAudio = false

        // Initialize Opus decoder
        opusDecoder = OpusDecoder(48000, 2)
        opusDecoder?.setFrameSize(960)

        coroutineScope.launch {
            var timeoutJob: Job? = launch {
                delay(MAX_RECORDING_DURATION_MS)
                if (isRecording) {
                    Log.info("Max recording duration reached")
                    handleMaxDuration()
                }
            }

            launch {
                captureAudioFrames()
            }.join()

            timeoutJob?.cancel()
        }
    }

    private suspend fun handleMaxDuration() {
        if (hasMeaningfulAudio()) {
            stopRecordingAndProcess()
        } else {
            Log.info("Max duration reached but no audio, continuing to record...")
            isRecording = true
            hasReceivedAudio = false
            pcmData.clear()
            opusDecoder?.close()
            opusDecoder = OpusDecoder(48000, 2)
            opusDecoder?.setFrameSize(960)
            textChannel.createMessage(
                localizationService.getString(
                    key = LocalizationKeys.ASSISTANT_NO_AUDIO,
                    guildId = guildId,
                    discordLocale = discordLocale
                )
            )
            // Restart timeout
            coroutineScope.launch {
                delay(MAX_RECORDING_DURATION_MS)
                if (isRecording && !hasMeaningfulAudio()) {
                    Log.info("Max recording duration reached again, stopping")
                    handleMaxDuration()
                }
            }
        }
    }

    private suspend fun captureAudioFrames() {
        val streams = voiceConnection?.streams
        Log.info("Starting captureAudioFrames. Streams available: ${streams != null}, streams: $streams")

        if (streams == null) {
            Log.warning("Streams is null! Cannot capture audio.")
            return
        }

        val incomingAudio = streams.incomingAudioFrames
        Log.info("Collecting incoming audio frames. IncomingAudio: $incomingAudio")

        // Track time since last received frame
        var lastFrameTime = System.currentTimeMillis()

        // Use a channel to handle both audio frames and periodic silence checks
        coroutineScope.launch {
            while (isRecording) {
                delay(100) // Check every 100ms

                val silenceDuration = System.currentTimeMillis() - lastFrameTime

                // Only stop if we've received audio before and now detecting silence
                if (hasReceivedAudio && silenceDuration >= SILENCE_DURATION_MS) {
                    Log.info("Silence detected after audio (${silenceDuration}ms >= ${SILENCE_DURATION_MS}ms), stopping recording")
                    stopRecordingAndProcess()
                    return@launch
                } else if (!hasReceivedAudio && silenceDuration >= SILENCE_DURATION_MS) {
                    Log.info("Silence detected but no audio yet, continuing to record...")
                    // Send reminder message periodically (every 3 seconds of silence)
                    if (silenceDuration % 3000 < 200) {
                        textChannel.createMessage(
                            localizationService.getString(
                                key = LocalizationKeys.ASSISTANT_NO_AUDIO,
                                guildId = guildId,
                                discordLocale = discordLocale
                            )
                        )
                    }
                    // Reset timer and keep recording
                    lastFrameTime = System.currentTimeMillis()
                }
            }
        }

        incomingAudio.collect { (ssrc, frame) ->
            if (!isRecording) {
                return@collect
            }

            val data = frame.data

            // Update last frame time whenever we receive any frame
            lastFrameTime = System.currentTimeMillis()
            Log.info("Received audio frame: ssrc=$ssrc, dataSize=${data.size}")

            if (data.isNotEmpty()) {
                // Decode Opus packet to PCM
                try {
                    val decoded = opusDecoder?.decode(data)
                    if (decoded != null && decoded.isNotEmpty()) {
                        pcmData.addAll(decoded.asList())
                        hasReceivedAudio = true
                        Log.info("Decoded ${decoded.size} PCM samples. Total: ${pcmData.size}")
                    }
                } catch (e: Exception) {
                    Log.warning("Error decoding Opus packet: ${e.message}")
                }
            }
        }
    }

    private fun hasMeaningfulAudio(): Boolean {
        return pcmData.isNotEmpty()
    }

    private suspend fun stopRecordingAndProcess() {
        Log.info("stopRecordingAndProcess called. isRecording: $isRecording, pcmData size: ${pcmData.size}")
        if (!isRecording) return

        Log.info("Checking for meaningful audio...")
        if (!hasMeaningfulAudio()) {
            Log.warning("No meaningful audio recorded (only silence). PCM data size: ${pcmData.size}")
            // Reset state and continue recording - don't disconnect
            isRecording = true
            hasReceivedAudio = false
            pcmData.clear()
            opusDecoder?.close()
            opusDecoder = OpusDecoder(48000, 2)
            opusDecoder?.setFrameSize(960)
            textChannel.createMessage(
                localizationService.getString(
                    key = LocalizationKeys.ASSISTANT_NO_AUDIO,
                    guildId = guildId,
                    discordLocale = discordLocale
                )
            )
            return
        }

        isRecording = false

        textChannel.createMessage(
            localizationService.getString(
                key = LocalizationKeys.ASSISTANT_PROCESSING,
                guildId = guildId,
                discordLocale = discordLocale
            )
        )

        val transcription = whisperService.transcribePcm(pcmData.toShortArray())?.trim()

        if (transcription.isNullOrBlank() || transcription == "[BLANK_AUDIO]") {
            Log.info("No meaningful audio, restarting recording")
            restartRecording()
            return
        }

        Log.info("About to post user message to chat: $transcription")
        val userMsgTemplate = localizationService.getString(
            key = LocalizationKeys.ASSISTANT_USER_MESSAGE,
            guildId = guildId,
            discordLocale = discordLocale
        )
        Log.info("User message template: '$userMsgTemplate'")
        textChannel.createMessage(
            localizationService.getStringFormat(
                key = LocalizationKeys.ASSISTANT_USER_MESSAGE,
                guildId = guildId,
                discordLocale = discordLocale,
                arguments = arrayOf(transcription)
            )
        )
        Log.info("User message posted to chat")

        Log.info("Calling ollama with prompt: $transcription")
        val response = ollamaService.generateResponse(transcription, conversationHistory.toList())
        Log.info("Ollama response received: $response")
        if (response.isNullOrBlank()) {
            cleanup()
            textChannel.createMessage(
                localizationService.getString(
                    key = LocalizationKeys.ASSISTANT_OLLAMA_ERROR,
                    guildId = guildId,
                    discordLocale = discordLocale
                )
            )
            return
        }

        conversationHistory.add(ConversationMessage("user", transcription))
        conversationHistory.add(ConversationMessage("assistant", response))

        textChannel.createMessage(
            localizationService.getStringFormat(
                key = LocalizationKeys.ASSISTANT_BOT_MESSAGE,
                guildId = guildId,
                discordLocale = discordLocale,
                arguments = arrayOf(response)
            )
        )

        playTtsResponse(response)
    }

    private suspend fun playTtsResponse(response: String) {
        try {
            // Shutdown the recording voice connection - TTS needs its own
            voiceConnection?.shutdown()
            voiceConnection = null
            
            // Prevent auto-disconnect while assistant is using TTS
            guildLavaPlayerService.setPreventAutoDisconnect(true)
            
            ttsService.loadAndPlayMessage(guildLavaPlayerService, response)

            coroutineScope.launch {
                while (guildLavaPlayerService.getCurrentPlayingTrack() != null) {
                    delay(500)
                }
                // After TTS finishes, have player leave so we can reconnect for recording
                guildLavaPlayerService.stop()
                restartRecording()
            }
        } catch (e: Exception) {
            Log.error("Error playing TTS response", exception = e)
            restartRecording()
        }
    }

    private suspend fun restartRecording() {
        try {
            guildLavaPlayerService.undeafen()
        } catch (e: Exception) {
            Log.warning("Could not undeafen after TTS: ${e.message}")
        }
        
        // Reset state for next recording
        isRecording = false
        hasReceivedAudio = false
        pcmData.clear()
        opusDecoder?.close()
        opusDecoder = OpusDecoder(48000, 2)
        opusDecoder?.setFrameSize(960)
        
        // Reconnect to voice channel for recording
        connectToVoiceChannel()
        
        // Wait for connection to stabilize and Discord to establish audio stream
        delay(5000)
        
        // Verify connection before starting
        Log.info("Pre-recording check. VoiceConnection: ${voiceConnection}, Streams: ${voiceConnection?.streams}")
        
        // Start recording again
        textChannel.createMessage(
            localizationService.getString(
                key = LocalizationKeys.ASSISTANT_LISTENING,
                guildId = guildId,
                discordLocale = discordLocale
            )
        )
        startRecording()
    }

    private suspend fun finishAssistant() {
        try {
            guildLavaPlayerService.undeafen()
        } catch (e: Exception) {
            Log.warning("Could not undeafen after TTS: ${e.message}")
        }
        cleanup()
    }

    private suspend fun cleanup() {
        isProcessing = false
        isRecording = false
        pcmData.clear()

        opusDecoder?.close()
        opusDecoder = null

        voiceConnection?.let { connection ->
            try {
                connection.shutdown()
            } catch (e: Exception) {
                Log.warning("Error shutting down voice connection: ${e.message}")
            }
        }
        voiceConnection = null
    }

    fun isAssistantActive(): Boolean = isProcessing
}

data class ConversationMessage(
    val role: String,
    val content: String
)
