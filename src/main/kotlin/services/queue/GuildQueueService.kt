package es.wokis.services.queue

import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.channel.BaseVoiceChannelBehavior
import dev.kord.core.entity.channel.MessageChannel
import dev.kord.core.entity.interaction.ApplicationCommandInteraction
import es.wokis.dispatchers.AppDispatchers
import es.wokis.localization.LocalizationKeys
import es.wokis.services.lavaplayer.AudioPlayerManagerProvider
import es.wokis.services.lavaplayer.GuildLavaPlayerService
import es.wokis.services.localization.LocalizationService
import es.wokis.utils.getMemberVoiceChannel
import es.wokis.utils.orDefaultLocale
import kotlin.jvm.Throws

class GuildQueueService(
    private val audioPlayerManagerProvider: AudioPlayerManagerProvider,
    private val appDispatchers: AppDispatchers,
    private val localizationService: LocalizationService
) {

    private val guildQueues: MutableMap<Snowflake, GuildLavaPlayerService> = mutableMapOf()

    @Throws(IllegalStateException::class)
    suspend fun getOrCreateLavaPlayerService(interaction: ApplicationCommandInteraction): GuildLavaPlayerService {
        val locale = interaction.guildLocale.orDefaultLocale()
        val voiceChannel = interaction.getMemberVoiceChannel(interaction.kord)
            ?: throw IllegalStateException(localizationService.getString(LocalizationKeys.ERROR_NO_VOICE_CHANNEL, locale))
        val textChannel = interaction.channel.asChannelOrNull()
            ?: throw IllegalStateException(localizationService.getString(LocalizationKeys.ERROR_NO_TEXT_CHANNEL, locale))
        val guildId = interaction.data.guildId.value
            ?: throw IllegalStateException(localizationService.getString(LocalizationKeys.ERROR_NO_GUILD, locale))

        return getOrCreateLavaPlayerService(
            guildId = guildId,
            textChannel = textChannel,
            voiceChannel = voiceChannel
        )
    }

    fun getOrCreateLavaPlayerService(
        guildId: Snowflake,
        textChannel: MessageChannel,
        voiceChannel: BaseVoiceChannelBehavior
    ): GuildLavaPlayerService = guildQueues[guildId] ?: createLavaPlayer(
        guild = guildId,
        textChannel = textChannel,
        voiceChannel = voiceChannel
    )

    fun getLavaPlayerService(guildId: Snowflake): GuildLavaPlayerService? = guildQueues[guildId]

    private fun createLavaPlayer(
        guild: Snowflake,
        textChannel: MessageChannel,
        voiceChannel: BaseVoiceChannelBehavior
    ): GuildLavaPlayerService = GuildLavaPlayerService(
        appDispatchers = appDispatchers,
        textChannel = textChannel,
        voiceChannel = voiceChannel,
        audioPlayerManager = audioPlayerManagerProvider.createAudioPlayerManager(),
        localizationService = localizationService
    ).also {
        guildQueues[guild] = it
    }
}
