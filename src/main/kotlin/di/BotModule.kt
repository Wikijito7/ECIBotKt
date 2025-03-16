package es.wokis.di

import es.wokis.bot.Bot
import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.module

val botModule = module {
    factoryOf(::Bot)
}
