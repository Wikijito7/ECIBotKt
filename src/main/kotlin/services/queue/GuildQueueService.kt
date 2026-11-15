package es.wokis.services.queue

import dev.kord.common.Locale
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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

class GuildQueueService(
    private val audioPlayerManagerProvider: AudioPlayerManagerProvider,
    private val appDispatchers: AppDispatchers,
    private val localizationService: LocalizationService
) {

    private val guildQueues: MutableMap<Snowflake, GuildLavaPlayerService> = ConcurrentHashMap()
    private val closed = AtomicBoolean(false)
    private val lock = Any()

    suspend fun getOrCreateLavaPlayerService(interaction: ApplicationCommandInteraction): GuildLavaPlayerService {
        check(!closed.get()) { "The guild queue service is shutting down" }
        val voiceChannel = interaction.getMemberVoiceChannel(interaction.kord)
            ?: throw BotException.UserException.NotInVoiceChannelException()
        val textChannel = interaction.requireTextChannel()
        val guildId = interaction.requireGuildId()

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
        discordLocale: Locale? = null
    ): GuildLavaPlayerService = synchronized(lock) {
        check(!closed.get()) { "The guild queue service is shutting down" }
        guildQueues.computeIfAbsent(guildId) {
            createLavaPlayer(
                guild = guildId,
                textChannel = textChannel,
                voiceChannel = voiceChannel,
                discordLocale = discordLocale
            )
        }.apply {
            this.discordLocale = discordLocale
        }
    }

    fun getLavaPlayerService(guildId: Snowflake): GuildLavaPlayerService? = guildQueues[guildId]

    /**
     * Closes every per-guild engine/voice connection exactly once and then shuts the shared
     * Lavaplayer manager down. Safe to call from both normal shutdown and a JVM shutdown hook.
     */
    suspend fun shutdown() {
        val services = synchronized(lock) {
            if (!closed.compareAndSet(false, true)) return
            guildQueues.values.toList().also { guildQueues.clear() }
        }
        var failure: Throwable? = null
        services.forEach { service ->
            try {
                service.shutdown()
            } catch (error: Throwable) {
                if (failure == null) failure = error else failure?.addSuppressed(error)
            }
        }
        try {
            audioPlayerManagerProvider.close()
        } catch (error: Throwable) {
            if (failure == null) failure = error else failure?.addSuppressed(error)
        }
        failure?.let { throw it }
    }

    private fun createLavaPlayer(
        guild: Snowflake,
        textChannel: MessageChannel,
        voiceChannel: BaseVoiceChannelBehavior,
        discordLocale: Locale? = null
    ): GuildLavaPlayerService {
        val engine = audioPlayerManagerProvider.createPlayerEngine()
        return try {
            GuildLavaPlayerService(
                appDispatchers = appDispatchers,
                textChannel = textChannel,
                voiceChannel = voiceChannel,
                playerEngine = engine,
                localizationService = localizationService,
                guildId = guild,
                discordLocale = discordLocale
            )
        } catch (error: Throwable) {
            // A failed construction must not leak the engine created for this guild.
            engine.close()
            throw error
        }
    }
}

private suspend fun ApplicationCommandInteraction.requireTextChannel(): MessageChannel =
    channel.asChannelOrNull() ?: throw BotException.UserException.NotInTextChannelException()

private fun ApplicationCommandInteraction.requireGuildId(): Snowflake =
    data.guildId.value ?: throw BotException.UserException.NotInGuildException()
