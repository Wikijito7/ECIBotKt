package di

import es.wokis.dispatchers.AppDispatchers
import es.wokis.services.config.ConfigService
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val servicesModule = module {
    singleOf(::ConfigService)
    single { AppDispatchers() }
}
