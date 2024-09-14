package di

import es.wokis.dispatchers.AppDispatchers
import es.wokis.servivces.config.ConfigService
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val servicesModule = module {
    singleOf(::ConfigService)
    single { AppDispatchers() }
}
