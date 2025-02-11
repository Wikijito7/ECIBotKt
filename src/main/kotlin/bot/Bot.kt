package es.wokis.bot

import dev.kord.common.entity.ActivityType
import dev.kord.common.entity.DiscordBotActivity
import dev.kord.common.entity.PresenceStatus
import dev.kord.core.Kord
import dev.kord.core.behavior.channel.asChannelOfOrNull
import dev.kord.core.behavior.interaction.respondPublic
import dev.kord.core.entity.Message
import dev.kord.core.entity.channel.MessageChannel
import dev.kord.core.entity.interaction.GlobalChatInputCommandInteraction
import dev.kord.core.event.gateway.ReadyEvent
import dev.kord.core.event.interaction.ChatInputCommandInteractionCreateEvent
import dev.kord.core.event.message.MessageCreateEvent
import dev.kord.core.on
import dev.kord.gateway.ALL
import dev.kord.gateway.DiscordPresence
import dev.kord.gateway.Intents
import dev.kord.gateway.PrivilegedIntent
import dev.kord.rest.builder.interaction.string
import es.wokis.dispatchers.AppDispatchers
import es.wokis.services.config.ConfigService
import es.wokis.services.config.discordToken
import es.wokis.services.config.isDebugMode
import es.wokis.services.lavaplayer.GuildLavaPlayerService
import es.wokis.services.processor.MessageProcessorService
import org.koin.core.component.KoinComponent
import java.util.logging.Level
import java.util.logging.Logger

@OptIn(PrivilegedIntent::class)
class Bot(
    private val config: ConfigService,
    private val messageProcessor: MessageProcessorService,
    private val appDispatchers: AppDispatchers
) : KoinComponent {

    suspend fun start() {
        val debugMode = config.isDebugMode
        val bot = Kord(token = config.discordToken)

        bot.login {
            presence = getPresence(debugMode)
            intents = Intents.ALL
        }

        bot.on<ReadyEvent> {
            Logger.getLogger("ECIBotKt").log(Level.INFO, "${self.username} is ready")
        }

        bot.on<MessageCreateEvent> {
            if (message.author?.isBot != false) return@on
            processMessages(message)
        }

        // TODO: Remove this command and create a new one with the logic to play the song
        bot.createGlobalChatInputCommand(name = "manolete", description = "Play a song") {
            string(name = "song", description = "The song to play") {
                required = true
            }
        }

        // TODO: Create logic to add GuildService to a map of guilds and queue the sounds
        bot.on<ChatInputCommandInteractionCreateEvent> {
            val interaction = interaction as GlobalChatInputCommandInteraction
            val voiceChannelId = interaction.data.guildId.value?.let { guildId ->
                bot.getGuildOrNull(guildId)?.let { guild ->
                    guild.getMemberOrNull(interaction.user.id)?.let { member ->
                        member.getVoiceStateOrNull()?.channelId
                    }
                }
            } ?: interaction.respondPublic { content = "You need to be in a voice channel" }.let { return@on }
            GuildLavaPlayerService(
                appDispatchers = appDispatchers,
                textChannelId = interaction.channelId,
                voiceChannelId = voiceChannelId,
                onNoMatches = { channelId ->
                    bot.getChannel(channelId)?.asChannelOfOrNull<MessageChannel>()?.createMessage("No matches found")
                },
                onLoadFailed = { channelId, exception ->
                    bot.getChannel(channelId)?.asChannelOfOrNull<MessageChannel>()?.createMessage("Load failed: ${exception.message}")
                },
                onTrackLoaded = { channelId, title ->
                    bot.getChannel(channelId)?.asChannelOfOrNull<MessageChannel>()?.createMessage("Now playing: $title")
                }
            )
        }
    }

    private fun processMessages(message: Message) {
        messageProcessor.processReactions(message)
        messageProcessor.processMessage(message)
    }

    fun getPresence(debugMode: Boolean) = DiscordPresence(
        status = PresenceStatus.Online,
        afk = debugMode,
        game = DiscordBotActivity(
            name = if (debugMode) "~debug mode on" else "~bip-bop",
            type = ActivityType.Game
        )
    )
}
