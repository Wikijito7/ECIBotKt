package es.wokis.di

import es.wokis.bot.Bot
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val botModule = module {
    singleOf(::Bot)
}
