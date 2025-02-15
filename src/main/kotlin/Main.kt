package es.wokis

import es.wokis.bot.Bot
import es.wokis.di.botModule
import es.wokis.di.commandModule
import es.wokis.di.servicesModule
import es.wokis.utils.Log
import org.koin.core.context.startKoin
import org.koin.java.KoinJavaComponent.inject
import org.slf4j.LoggerFactory

private val bot: Bot by inject(Bot::class.java)
private val logger = LoggerFactory.getLogger(Bot::class.java)

suspend fun main() {
    Log.setLogger(logger)
    initKoin()
    bot.start()
}

fun initKoin() {
    startKoin {
        modules(botModule, servicesModule, commandModule)
    }
}
