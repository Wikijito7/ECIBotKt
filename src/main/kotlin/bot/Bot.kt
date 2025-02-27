package es.wokis.bot

import dev.kord.common.entity.ActivityType
import dev.kord.common.entity.DiscordBotActivity
import dev.kord.common.entity.PresenceStatus
import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.core.entity.Message
import dev.kord.core.event.gateway.ReadyEvent
import dev.kord.core.event.interaction.ButtonInteractionCreateEvent
import dev.kord.core.event.interaction.ChatInputCommandInteractionCreateEvent
import dev.kord.core.event.message.MessageCreateEvent
import dev.kord.core.event.user.VoiceStateUpdateEvent
import dev.kord.core.on
import dev.kord.gateway.ALL
import dev.kord.gateway.DiscordPresence
import dev.kord.gateway.Intents
import dev.kord.gateway.PrivilegedIntent
import es.wokis.services.commands.CommandHandlerService
import es.wokis.services.config.ConfigService
import es.wokis.services.processor.MessageProcessorService
import es.wokis.services.queue.GuildQueueService
import es.wokis.utils.Log
import kotlinx.coroutines.flow.collect
import org.koin.core.component.KoinComponent

@OptIn(PrivilegedIntent::class)
class Bot(
    private val configService: ConfigService,
    private val messageProcessorService: MessageProcessorService,
    private val commandHandlerService: CommandHandlerService,
    private val guildQueueService: GuildQueueService
) : KoinComponent {

    suspend fun start() {
        val bot = Kord(token = configService.config.discordBotToken)

        setUpEvents(bot)
        setUpCommands(bot)

        bot.login {
            presence = getPresence(debugMode = configService.config.debug)
            intents = Intents.ALL
        }
    }

    private suspend fun setUpCommands(bot: Kord) {
        bot.createGlobalApplicationCommands {
            commandHandlerService.onRegisterCommand(this)
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
            if (state.userId != bot.selfId) return@on
            if (state.data.channelId == null) {
                handleDisconnectEvent(state.guildId)
            }
        }

        bot.on<ChatInputCommandInteractionCreateEvent> {
            val response = interaction.deferPublicResponse()
            commandHandlerService.onExecute(interaction, response)
        }

        bot.on<ButtonInteractionCreateEvent> {
            interaction.deferPublicMessageUpdate()
            commandHandlerService.onInteract(interaction)
        }
    }

    private suspend fun handleDisconnectEvent(guildId: Snowflake) {
        guildQueueService.getLavaPlayerService(guildId)?.handleDisconnectEvent()
    }

    private fun processMessages(message: Message) {
        messageProcessorService.processReactions(message)
        messageProcessorService.processMessage(message)
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
