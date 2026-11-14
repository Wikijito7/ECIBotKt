package es.wokis.di

import es.wokis.domain.locale.LocaleRepository
import es.wokis.repositories.locale.LocalJsonLocaleRepository
import es.wokis.repositories.locale.LocaleJson
import org.koin.dsl.bind
import org.koin.dsl.module

val repositoryModule = module {
    single { LocalJsonLocaleRepository(LocaleJson.json) } bind LocaleRepository::class
}
