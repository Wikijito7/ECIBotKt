package es.wokis.di

import es.wokis.commands.test.TestCommand
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val commandModule = module {
    singleOf(::TestCommand)
}
