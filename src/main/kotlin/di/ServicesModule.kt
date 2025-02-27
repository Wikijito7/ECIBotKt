package es.wokis.di

import es.wokis.dispatchers.AppDispatchers
import es.wokis.dispatchers.AppDispatchersImpl
import es.wokis.services.commands.CommandHandlerService
import es.wokis.services.commands.CommandHandlerServiceImpl
import es.wokis.services.config.ConfigService
import es.wokis.services.localization.LocalizationService
import es.wokis.services.lavaplayer.AudioPlayerManagerProvider
import es.wokis.services.processor.MessageProcessorService
import es.wokis.services.queue.GuildQueueService
import es.wokis.services.tts.TTSService
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val servicesModule = module {
    singleOf(::ConfigService)
    singleOf(::MessageProcessorService)
    singleOf(::AudioPlayerManagerProvider)
    singleOf(::GuildQueueService)
    singleOf(::CommandHandlerServiceImpl) { bind<CommandHandlerService>() }
    singleOf(::LocalizationService)
    singleOf(::TTSService)

    single<AppDispatchers> { AppDispatchersImpl() }
}
