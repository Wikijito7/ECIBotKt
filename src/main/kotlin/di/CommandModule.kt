package es.wokis.di

import es.wokis.commands.queue.QueueCommand
import es.wokis.commands.test.PlayCommand
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val commandModule = module {
    singleOf(::PlayCommand)
    singleOf(::QueueCommand)
}
