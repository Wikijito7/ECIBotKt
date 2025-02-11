package es.wokis.utils

import es.wokis.dispatchers.AppDispatchers
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.coroutines.CoroutineContext

fun createCoroutineScope(tag: String, appDispatchers: AppDispatchers) =
    CoroutineScope(SupervisorJob() + appDispatchers.io + coroutineExceptionHandler(tag))

private fun coroutineExceptionHandler(tag: String): CoroutineContext = CoroutineExceptionHandler { _, throwable ->
    Logger.getLogger("ECIBotKt").log(Level.SEVERE, "There's been an error on $tag.", throwable)
}
