package es.wokis.di

import es.wokis.repositories.locale.LocalJsonLocaleRepository
import kotlinx.serialization.json.Json
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val repositoryModule = module {
    single {
        Json {
            prettyPrint = true
            ignoreUnknownKeys = true
        }
    }
    singleOf(::LocalJsonLocaleRepository)
}
