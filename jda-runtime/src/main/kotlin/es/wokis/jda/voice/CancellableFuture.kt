package es.wokis.jda.voice

import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.ExecutionException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Suspends until [this] future completes instead of blocking a coroutine thread.
 *
 * Cancelling the coroutine resumes the continuation immediately, so the adapter can start its
 * cleanup on time instead of waiting for a blocking `get` timeout. [cancelFutureOnCancellation]
 * controls whether the future itself is cancelled: shared waiter signals keep `false` because
 * JDA may still complete them after the caller stopped waiting.
 *
 * Completion exceptions are unwrapped so callers receive the application exception that was
 * passed to [CompletableFuture.completeExceptionally], not a [ExecutionException] wrapper.
 */
internal suspend fun <T> CompletableFuture<T>.awaitCancellable(
    cancelFutureOnCancellation: Boolean = true,
): T = suspendCancellableCoroutine { continuation ->
    whenComplete { value, error ->
        if (error == null) {
            continuation.resume(value)
        } else {
            continuation.resumeWithException(error.unwrapCompletion())
        }
    }

    if (cancelFutureOnCancellation) {
        continuation.invokeOnCancellation { cancel(false) }
    }
}

private fun Throwable.unwrapCompletion(): Throwable = when (this) {
    is ExecutionException -> cause ?: this
    is CompletionException -> cause ?: this
    else -> this
}
