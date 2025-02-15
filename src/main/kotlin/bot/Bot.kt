package es.wokis.bot

import dev.kord.common.entity.ActivityType
import dev.kord.common.entity.DiscordBotActivity
import dev.kord.common.entity.PresenceStatus
import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.core.behavior.interaction.respondPublic
import dev.kord.core.entity.Message
import dev.kord.core.event.gateway.ReadyEvent
import dev.kord.core.event.interaction.ChatInputCommandInteractionCreateEvent
import dev.kord.core.event.message.MessageCreateEvent
import dev.kord.core.event.user.VoiceStateUpdateEvent
import dev.kord.core.on
import dev.kord.gateway.ALL
import dev.kord.gateway.DiscordPresence
import dev.kord.gateway.Intents
import dev.kord.gateway.PrivilegedIntent
import dev.kord.rest.builder.interaction.string
import es.wokis.services.config.ConfigService
import es.wokis.services.config.discordToken
import es.wokis.services.config.isDebugMode
import es.wokis.services.processor.MessageProcessorService
import es.wokis.services.queue.GuildQueueDispatcher
import es.wokis.utils.Log
import es.wokis.utils.getMemberVoiceChannel
import kotlinx.coroutines.flow.collect
import org.koin.core.component.KoinComponent

@OptIn(PrivilegedIntent::class)
class Bot(
    private val config: ConfigService,
    private val messageProcessor: MessageProcessorService,
    private val guildQueueDispatcher: GuildQueueDispatcher
) : KoinComponent {

    suspend fun start() {
        val debugMode = config.isDebugMode
        val bot = Kord(token = config.discordToken)

        setUpEvents(bot)
        setUpCommands(bot)

        bot.login {
            presence = getPresence(debugMode)
            intents = Intents.ALL
        }
    }

    private suspend fun setUpCommands(bot: Kord) {
        // TODO: Remove these commands and create the real ones
        bot.createGlobalApplicationCommands {
            input(name = "test", description = "test test") {
                string(name = "pepe", description = "popopo") {
                    required = true
                }
            }
        }.collect()
    }

    private fun setUpEvents(bot: Kord) {
        bot.on<ReadyEvent> {
            Log.info("${self.username} is ready")
        }

        bot.on<MessageCreateEvent> {
            if (message.author?.isBot != false) return@on
            processMessages(message)
        }

        bot.on<VoiceStateUpdateEvent> {
            if (state.data.channelId == null) {
                handleDisconnectEvent(state.guildId)
            }
        }

        bot.on<ChatInputCommandInteractionCreateEvent> {
            val voiceChannel = interaction.getMemberVoiceChannel(bot)
                ?: interaction.respondPublic { content = "You need to be in a voice channel" }.let { return@on }
            val textChannel = interaction.channel.asChannelOrNull()
                ?: interaction.respondPublic { content = "You need to be in a text channel" }.let { return@on }
            val guildId = interaction.data.guildId.value
                ?: interaction.respondPublic { content = "You need to be in a guild" }.let { return@on }
            val input: String = interaction.command.strings["pepe"]?.takeUnless { it.isEmpty() }
                ?: interaction.respondPublic { content = "You need to give provide a url" }.let { return@on }
            interaction.respondPublic { content = "Searching the song" }
            guildQueueDispatcher.getOrCreateLavaPlayerService(
                guildId = guildId,
                textChannel = textChannel,
                voiceChannel = voiceChannel
            ).loadAndPlay(input)
        }
    }

    private suspend fun handleDisconnectEvent(guildId: Snowflake) {
        guildQueueDispatcher.getLavaPlayerService(guildId)?.handleDisconnectEvent()
    }

    private fun processMessages(message: Message) {
        messageProcessor.processReactions(message)
        messageProcessor.processMessage(message)
    }

    fun getPresence(debugMode: Boolean) = DiscordPresence(
        status = if (debugMode) PresenceStatus.Idle else PresenceStatus.Online,
        afk = false,
        game = DiscordBotActivity(
            name = if (debugMode) "~debug mode on" else "~bip-bop",
            type = ActivityType.Game
        )
    )
}
