package es.wokis.di

import es.wokis.domain.GetFloweryVoicesUseCase
import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.module

val domainModule = module {
    factoryOf(::GetFloweryVoicesUseCase)
}
