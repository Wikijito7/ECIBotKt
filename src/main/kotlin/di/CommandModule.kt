package es.wokis.di

import es.wokis.commands.queue.QueueCommand
import commands.play.PlayCommand
import es.wokis.commands.shuffle.ShuffleCommand
import es.wokis.commands.skip.SkipCommand
import es.wokis.commands.tts.TTSCommand
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val commandModule = module {
    singleOf(::PlayCommand)
    singleOf(::QueueCommand)
    singleOf(::SkipCommand)
    singleOf(::ShuffleCommand)
    singleOf(::TTSCommand)
}
