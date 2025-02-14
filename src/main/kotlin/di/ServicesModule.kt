package di

import es.wokis.dispatchers.AppDispatchers
import es.wokis.dispatchers.AppDispatchersImpl
import es.wokis.services.config.ConfigService
import es.wokis.services.lavaplayer.AudioPlayerManagerProvider
import es.wokis.services.processor.MessageProcessorService
import es.wokis.services.queue.GuildQueueDispatcher
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val servicesModule = module {
    singleOf(::ConfigService)
    singleOf(::MessageProcessorService)
    singleOf(::AudioPlayerManagerProvider)
    singleOf(::GuildQueueDispatcher)

    single<AppDispatchers> { AppDispatchersImpl() }
}
