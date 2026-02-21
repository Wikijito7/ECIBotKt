package es.wokis.services.assistant

import dev.kord.common.Locale
import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.core.behavior.channel.BaseVoiceChannelBehavior
import dev.kord.core.entity.channel.MessageChannel
import dev.kord.core.entity.interaction.ApplicationCommandInteraction
import es.wokis.dispatchers.AppDispatchers
import es.wokis.services.config.ConfigService
import es.wokis.services.localization.LocalizationService
import es.wokis.services.lavaplayer.GuildLavaPlayerService
import es.wokis.services.tts.TTSService
import es.wokis.utils.getMemberVoiceChannel

class AssistantServiceFactory(
    private val appDispatchers: AppDispatchers,
    private val configService: ConfigService,
    private val whisperService: WhisperService,
    private val ollamaService: OllamaService,
    private val ttsService: TTSService,
    private val localizationService: LocalizationService
) {

    suspend fun createAssistantService(
        interaction: ApplicationCommandInteraction,
        guildLavaPlayerService: GuildLavaPlayerService
    ): AssistantService {
        val kord = interaction.kord
        val voiceChannel = interaction.getMemberVoiceChannel(kord)
            ?: throw IllegalStateException("User not in voice channel")
        val textChannel = interaction.channel.asChannel()
        val guildId = interaction.data.guildId.value
            ?: throw IllegalStateException("No guild ID")

        return AssistantService(
            appDispatchers = appDispatchers,
            configService = configService,
            whisperService = whisperService,
            ollamaService = ollamaService,
            ttsService = ttsService,
            localizationService = localizationService,
            kord = kord,
            textChannel = textChannel,
            voiceChannel = voiceChannel,
            guildId = guildId,
            guildLavaPlayerService = guildLavaPlayerService
        )
    }
}
