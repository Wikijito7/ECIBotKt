package es.wokis.services.queue

import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.channel.BaseVoiceChannelBehavior
import dev.kord.core.entity.channel.MessageChannel
import dev.kord.core.entity.interaction.ApplicationCommandInteraction
import es.wokis.dispatchers.AppDispatchers
import es.wokis.exceptions.BotException
import es.wokis.services.lavaplayer.AudioPlayerManagerProvider
import es.wokis.services.lavaplayer.GuildLavaPlayerService
import es.wokis.services.localization.LocalizationService
import es.wokis.utils.getMemberVoiceChannel

class GuildQueueService(
    private val audioPlayerManagerProvider: AudioPlayerManagerProvider,
    private val appDispatchers: AppDispatchers,
    private val localizationService: LocalizationService
) {

    private val guildQueues: MutableMap<Snowflake, GuildLavaPlayerService> = mutableMapOf()

    suspend fun getOrCreateLavaPlayerService(interaction: ApplicationCommandInteraction): GuildLavaPlayerService {
        val voiceChannel = interaction.getMemberVoiceChannel(interaction.kord)
            ?: throw BotException.UserException.NotInVoiceChannelException()
        val textChannel = interaction.channel.asChannelOrNull()
            ?: throw BotException.UserException.NotInTextChannelException()
        val guildId = interaction.data.guildId.value
            ?: throw BotException.UserException.NotInGuildException()

        return getOrCreateLavaPlayerService(
            guildId = guildId,
            textChannel = textChannel,
            voiceChannel = voiceChannel,
            discordLocale = interaction.guildLocale
        )
    }

    fun getOrCreateLavaPlayerService(
        guildId: Snowflake,
        textChannel: MessageChannel,
        voiceChannel: BaseVoiceChannelBehavior,
        discordLocale: dev.kord.common.Locale? = null
    ): GuildLavaPlayerService = guildQueues[guildId]?.apply {
        this.discordLocale = discordLocale
    } ?: createLavaPlayer(
        guild = guildId,
        textChannel = textChannel,
        voiceChannel = voiceChannel,
        discordLocale = discordLocale
    )

    fun getLavaPlayerService(guildId: Snowflake): GuildLavaPlayerService? = guildQueues[guildId]

    private fun createLavaPlayer(
        guild: Snowflake,
        textChannel: MessageChannel,
        voiceChannel: BaseVoiceChannelBehavior,
        discordLocale: dev.kord.common.Locale? = null
    ): GuildLavaPlayerService = GuildLavaPlayerService(
        appDispatchers = appDispatchers,
        textChannel = textChannel,
        voiceChannel = voiceChannel,
        audioPlayerManager = audioPlayerManagerProvider.createAudioPlayerManager(),
        localizationService = localizationService,
        guildId = guild,
        discordLocale = discordLocale
    ).also {
        guildQueues[guild] = it
    }
}
