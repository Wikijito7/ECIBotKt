package es.wokis.di

import es.wokis.domain.locale.LocaleRepository
import es.wokis.repositories.locale.LocalJsonLocaleRepository
import kotlinx.serialization.json.Json
import org.koin.dsl.bind
import org.koin.dsl.module

val repositoryModule = module {
    single {
        Json {
            prettyPrint = true
            ignoreUnknownKeys = true
        }
    }
    single { LocalJsonLocaleRepository(get()) } bind LocaleRepository::class
}
