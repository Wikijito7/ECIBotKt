package es.wokis

import di.servicesModule
import es.wokis.bot.Bot
import es.wokis.di.botModule
import org.koin.core.context.startKoin
import org.koin.java.KoinJavaComponent.inject

private val bot: Bot by inject(Bot::class.java)

suspend fun main() {
    initKoin()
    bot.start()
}

fun initKoin() {
    startKoin {
        modules(botModule, servicesModule)
    }
}
