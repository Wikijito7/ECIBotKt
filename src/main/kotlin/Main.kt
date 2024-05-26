package es.wokis

import dev.kord.common.entity.ActivityType
import dev.kord.common.entity.DiscordBotActivity
import dev.kord.common.entity.PresenceStatus
import dev.kord.core.Kord
import dev.kord.core.event.message.MessageCreateEvent
import dev.kord.core.on
import dev.kord.gateway.ALL
import dev.kord.gateway.DiscordPresence
import dev.kord.gateway.Intents
import dev.kord.gateway.PrivilegedIntent


@OptIn(PrivilegedIntent::class)
suspend fun main() {
    val debugMode = false // TODO: Read debug mode from data file
    val bot = Kord(token = "") // TODO: Get token from data file

    bot.login {
        presence = getPresence(debugMode)
        intents = Intents.ALL
    }

    bot.on<MessageCreateEvent> {
        if (message.author?.isBot != false) return@on
        processMessages(message.data.content)
    }
}

fun processMessages(content: String) {
    // TODO: Implement processors
}

fun getPresence(debugMode: Boolean) = DiscordPresence(
    status = PresenceStatus.Online,
    afk = debugMode,
    game = DiscordBotActivity(
        name = if (debugMode) "~debug mode on" else "~bip-bop",
        type = ActivityType.Game
    )
)
