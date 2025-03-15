package es.wokis.di

import es.wokis.commands.queue.QueueCommand
import commands.play.PlayCommand
import es.wokis.commands.player.PlayerCommand
import es.wokis.commands.shuffle.ShuffleCommand
import es.wokis.commands.skip.SkipCommand
import es.wokis.commands.sounds.SoundsCommand
import es.wokis.commands.tts.TTSCommand
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val commandModule = module {
    factoryOf(::PlayCommand)
    factoryOf(::QueueCommand)
    factoryOf(::SkipCommand)
    factoryOf(::ShuffleCommand)
    factoryOf(::TTSCommand)
    factoryOf(::PlayerCommand)
    factoryOf(::SoundsCommand)
}
