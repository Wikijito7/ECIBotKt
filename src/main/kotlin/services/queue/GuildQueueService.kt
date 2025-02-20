package es.wokis.services.queue

import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.channel.BaseVoiceChannelBehavior
import dev.kord.core.entity.channel.MessageChannel
import es.wokis.dispatchers.AppDispatchers
import es.wokis.services.lavaplayer.AudioPlayerManagerProvider
import es.wokis.services.lavaplayer.GuildLavaPlayerService
import es.wokis.services.localization.LocalizationService

class GuildQueueService(
    private val audioPlayerManagerProvider: AudioPlayerManagerProvider,
    private val appDispatchers: AppDispatchers,
    private val localizationService: LocalizationService
) {

    private val guildQueues: MutableMap<Snowflake, GuildLavaPlayerService> = mutableMapOf()

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
