package es.wokis.di

import es.wokis.domain.GetFloweryVoicesUseCase
import es.wokis.domain.locale.GetGuildLocaleUseCase
import es.wokis.domain.locale.SetGuildLocaleUseCase
import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.module

val domainModule = module {
    factoryOf(::GetFloweryVoicesUseCase)
    factoryOf(::GetGuildLocaleUseCase)
    factoryOf(::SetGuildLocaleUseCase)
}
