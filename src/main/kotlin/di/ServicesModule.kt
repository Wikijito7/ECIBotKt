package es.wokis.di

import es.wokis.dispatchers.AppDispatchers
import es.wokis.dispatchers.AppDispatchersImpl
import es.wokis.services.commands.CommandHandlerService
import es.wokis.services.commands.CommandHandlerServiceImpl
import es.wokis.services.config.ConfigMigrationService
import es.wokis.services.config.ConfigService
import es.wokis.services.error.ErrorHandlerService
import es.wokis.services.lavaplayer.AudioPlayerManagerProvider
import es.wokis.services.localization.LocalizationService
import es.wokis.services.player.PlayerChannelService
import es.wokis.services.processor.MessageProcessorService
import es.wokis.services.queue.GuildQueueService
import es.wokis.services.radio.RadioService
import es.wokis.services.tts.TTSService
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val servicesModule = module {
    singleOf(::ConfigMigrationService)
    singleOf(::ConfigService)
    factoryOf(::MessageProcessorService)
    factoryOf(::AudioPlayerManagerProvider)
    singleOf(::GuildQueueService)
    factoryOf(::CommandHandlerServiceImpl) { bind<CommandHandlerService>() }
    singleOf(::LocalizationService)
    singleOf(::TTSService)
    singleOf(::RadioService)
    factoryOf(::PlayerChannelService)
    factoryOf(::ErrorHandlerService)

    single<AppDispatchers> { AppDispatchersImpl() }
}
