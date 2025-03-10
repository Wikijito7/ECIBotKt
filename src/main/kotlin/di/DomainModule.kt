package es.wokis.di

import es.wokis.domain.GetFloweryVoicesUseCase
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val domainModule = module {
    singleOf(::GetFloweryVoicesUseCase)
}
