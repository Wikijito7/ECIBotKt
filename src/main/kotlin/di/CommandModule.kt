package es.wokis.di

import es.wokis.commands.queue.QueueCommand
import commands.play.PlayCommand
import es.wokis.commands.player.PlayerCommand
import es.wokis.commands.radio.RadioGroupCommand
import es.wokis.commands.radio.subcommands.countrycodes.RadioCountryCodesCommand
import es.wokis.commands.radio.subcommands.list.RadioListCommand
import es.wokis.commands.radio.subcommands.play.RadioPlayCommand
import es.wokis.commands.radio.subcommands.random.RadioRandomCommand
import es.wokis.commands.radio.subcommands.search.RadioSearchCountryCodeCommand
import es.wokis.commands.radio.subcommands.search.RadioSearchGroupCommand
import es.wokis.commands.radio.subcommands.search.RadioSearchNameCommand
import es.wokis.commands.shuffle.ShuffleCommand
import es.wokis.commands.skip.SkipCommand
import es.wokis.commands.sound.SoundCommand
import es.wokis.commands.sounds.SoundsCommand
import es.wokis.commands.next.NextCommand
import es.wokis.commands.reconnect.ReconnectCommand
import es.wokis.commands.disconnect.DisconnectCommand
import es.wokis.commands.tts.TTSCommand
import es.wokis.commands.locale.LocaleCommand
import es.wokis.domain.locale.GetGuildLocaleUseCase
import es.wokis.domain.locale.SetGuildLocaleUseCase
import es.wokis.repositories.locale.LocalJsonLocaleRepository
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val commandModule = module {
    // Locale Repository and Use Cases
    singleOf(::LocalJsonLocaleRepository)
    factoryOf(::GetGuildLocaleUseCase)
    factoryOf(::SetGuildLocaleUseCase)
    factoryOf(::PlayCommand)
    factoryOf(::SoundCommand)
    factoryOf(::QueueCommand)
    factoryOf(::SkipCommand)
    factoryOf(::ShuffleCommand)
    factoryOf(::TTSCommand)
    factoryOf(::PlayerCommand)
    factoryOf(::SoundsCommand)
    factoryOf(::ReconnectCommand)
    factoryOf(::NextCommand)
    factoryOf(::DisconnectCommand)
    factoryOf(::RadioGroupCommand)
    factoryOf(::RadioPlayCommand)
    factoryOf(::RadioListCommand)
    factoryOf(::RadioSearchGroupCommand)
    factoryOf(::RadioSearchNameCommand)
    factoryOf(::RadioSearchCountryCodeCommand)
    factoryOf(::RadioRandomCommand)
    factoryOf(::RadioCountryCodesCommand)
    factoryOf(::LocaleCommand)
}
