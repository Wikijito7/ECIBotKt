package es.wokis.jda.voice

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.ExecutionException
import kotlin.test.assertFailsWith

class CancellableFutureTest {

    @Test
    fun `Given a completed future When awaited Then the value is propagated`() = runBlocking {
        // Given
        val future = CompletableFuture<String>()
        future.complete("tone ready")

        // Then
        assertEquals("tone ready", future.awaitCancellable())
    }

    @Test
    fun `Given an exceptionally completed future When awaited Then the original exception is propagated`() = runBlocking {
        // Given
        val future = CompletableFuture<String>()
        val failure = IllegalStateException("the voice source failed")
        future.completeExceptionally(failure)

        // When
        val thrown = assertFailsWith<IllegalStateException> { future.awaitCancellable<String>() }

        // Then: the caller receives the application exception, not an ExecutionException wrapper.
        // kotlinx.coroutines may copy the throwable for stack-trace recovery, so compare its
        // identity-defining data rather than the instance.
        assertEquals(failure.message, thrown.message)
    }

    @Test
    fun `Given a future completed with a CompletionException When awaited Then its cause is propagated`() = runBlocking {
        // Given
        val future = CompletableFuture<String>()
        val cause = VoiceConnectionException("the connection was lost")
        future.completeExceptionally(CompletionException(cause))

        // When
        val thrown = assertFailsWith<VoiceConnectionException> { future.awaitCancellable<String>() }

        // Then
        assertEquals(cause.message, thrown.message)
    }

    @Test
    fun `Given a future completed with an ExecutionException When awaited Then its cause is propagated`() = runBlocking {
        // Given
        val future = CompletableFuture<String>()
        val cause = IllegalStateException("wrapped failure")
        future.completeExceptionally(ExecutionException(cause))

        // When
        val thrown = assertFailsWith<IllegalStateException> { future.awaitCancellable<String>() }

        // Then
        assertEquals(cause.message, thrown.message)
    }

    @Test
    fun `Given a pending future When the coroutine is cancelled Then the underlying future is cancelled promptly`() =
        runBlocking {
            // Given: UNDISPATCHED guarantees the continuation is registered before cancellation.
            val future = CompletableFuture<Unit>()
            val job = launch(start = CoroutineStart.UNDISPATCHED) {
                future.awaitCancellable()
            }
            assertFalse(future.isDone)

            // When
            withTimeout(1_000) { job.cancelAndJoin() }

            // Then
            assertTrue(job.isCancelled)
            assertTrue(future.isCancelled, "cancelling the wait must cancel the underlying future")
        }

    @Test
    fun `Given a shared signal When the coroutine is cancelled Then the underlying future stays pending`() = runBlocking {
        // Given: shared waiter signals must survive one playback stopping its wait.
        val future = CompletableFuture<String>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            future.awaitCancellable(cancelFutureOnCancellation = false)
        }

        // When
        withTimeout(1_000) { job.cancelAndJoin() }

        // Then
        assertTrue(job.isCancelled)
        assertFalse(future.isCancelled)
        assertFalse(future.isDone)

        // The shared signal still delivers its value to a later waiter.
        future.complete("connection lost")
        assertEquals("connection lost", future.awaitCancellable())
    }
}
