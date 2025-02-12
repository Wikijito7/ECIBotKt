package es.wokis.bot

import dev.kord.common.entity.ActivityType
import dev.kord.common.entity.DiscordBotActivity
import dev.kord.common.entity.PresenceStatus
import dev.kord.core.Kord
import dev.kord.core.behavior.interaction.respondPublic
import dev.kord.core.entity.Message
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
import es.wokis.services.config.youtubeOauth2Token
import es.wokis.services.lavaplayer.GuildLavaPlayerService
import es.wokis.services.processor.MessageProcessorService
import es.wokis.utils.Log
import es.wokis.utils.getMemberVoiceChannel
import org.koin.core.component.KoinComponent
import org.slf4j.LoggerFactory

@OptIn(PrivilegedIntent::class)
class Bot(
    private val config: ConfigService,
    private val messageProcessor: MessageProcessorService,
    private val appDispatchers: AppDispatchers
) : KoinComponent {

    // TODO: Remove this variable and create Guild queues
    private var guildLavaPlayerService: GuildLavaPlayerService? = null

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
        }
    }

    private fun setUpEvents(bot: Kord) {
        bot.on<ReadyEvent> {
            Log.info("${self.username} is ready")
        }

        bot.on<MessageCreateEvent> {
            if (message.author?.isBot != false) return@on
            processMessages(message)
        }

        // TODO: Create logic to add GuildService to a map of guilds and queue the sounds
        bot.on<ChatInputCommandInteractionCreateEvent> {
            val voiceChannel = interaction.getMemberVoiceChannel(bot)
                ?: interaction.respondPublic { content = "You need to be in a voice channel" }.let { return@on }
            val textChannel = interaction.channel.asChannelOrNull()
                ?: interaction.respondPublic { content = "You need to be in a text channel" }.let { return@on }
            val input: String = interaction.command.strings["pepe"]?.takeUnless { it.isEmpty() }
                ?: interaction.respondPublic { content = "You need to give provide a url" }.let { return@on }
            interaction.respondPublic { content = "Searching the song" }
            // TODO: Create only one instance of GuildLavaPlayerService per guild and use it
            if (guildLavaPlayerService == null) {
                guildLavaPlayerService = GuildLavaPlayerService(
                    appDispatchers = appDispatchers,
                    textChannel = textChannel,
                    voiceChannel = voiceChannel,
                    youtubeOauth2Token = config.youtubeOauth2Token
                )
            }
            guildLavaPlayerService?.loadAndPlay(input)
        }
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
