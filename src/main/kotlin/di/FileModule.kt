package di

import es.wokis.helper.ConfigHelper
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val fileModule = module {
    singleOf(::ConfigHelper)
}
