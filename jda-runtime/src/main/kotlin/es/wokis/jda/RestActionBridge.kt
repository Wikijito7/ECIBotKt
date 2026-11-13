package es.wokis.jda

import kotlinx.coroutines.suspendCancellableCoroutine
import net.dv8tion.jda.api.requests.RestAction
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Minimal callback-to-suspend bridge for JDA [RestAction]s.
 *
 * JDA executes `queue` callbacks on its own request threads. The continuation is resumed at
 * most once and a synchronous `queue` failure (for example a rejected executor) is propagated
 * to the caller instead of escaping the gateway thread.
 */
internal suspend fun <T> RestAction<T>.awaitRestAction(): T = suspendCancellableCoroutine { continuation ->
    try {
        queue(
            { value -> if (continuation.isActive) continuation.resume(value) },
            { error -> if (continuation.isActive) continuation.resumeWithException(error) },
        )
    } catch (error: Throwable) {
        if (continuation.isActive) continuation.resumeWithException(error)
    }
}

/**
 * Awaits a [RestAction] whose success value is irrelevant (typically `Void`).
 *
 * JDA represents empty responses with a null success value, so the result must not be
 * treated as a non-null payload.
 */
@Suppress("UNCHECKED_CAST")
internal suspend fun RestAction<*>.awaitRestActionCompletion() {
    (this as RestAction<Any?>).awaitRestAction()
}
