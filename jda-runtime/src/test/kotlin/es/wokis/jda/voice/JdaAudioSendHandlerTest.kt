package es.wokis.jda.voice

import es.wokis.voice.AudioFrame
import es.wokis.voice.AudioFrameFormat
import es.wokis.voice.AudioFrameSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertFailsWith

class JdaAudioSendHandlerTest {

    @Test
    fun `Given a fresh handler When canProvide is called Then prefetch exactly one frame`() {
        // Given
        val source = CountingFrameSource(frameCount = 2)
        val handler = JdaAudioSendHandler(source)

        // When
        val first = handler.canProvide()
        val second = handler.canProvide()

        // Then
        assertTrue(first)
        assertTrue(second)
        assertEquals(1, source.calls.get(), "canProvide must not consume a second frame while one is prefetched")
        assertEquals(0, handler.framesProvided)
    }

    @Test
    fun `Given a prefetched frame When provide20MsAudio is called twice Then the source is read only once`() {
        // Given
        val source = CountingFrameSource(frameCount = 2)
        val handler = JdaAudioSendHandler(source)
        assertTrue(handler.canProvide())

        // When
        val first = handler.provide20MsAudio()
        val second = handler.provide20MsAudio()

        // Then
        assertEquals(0x78.toByte(), first!!.array()[0])
        assertNull(second)
        assertEquals(1, source.calls.get())
        assertEquals(1, handler.framesProvided)

        // The next prefetch advances the source exactly once.
        assertTrue(handler.canProvide())
        assertEquals(2, source.calls.get())
    }

    @Test
    fun `Given a prefetched frame When the audio buffer is inspected Then it is an independent array-backed copy`() {
        // Given
        val source = CountingFrameSource(frameCount = 1)
        val handler = JdaAudioSendHandler(source)
        assertTrue(handler.canProvide())

        // When
        val buffer = handler.provide20MsAudio()!!

        // Then: JDA receives an array-backed heap buffer over exactly the frame bytes.
        assertTrue(buffer.hasArray())
        assertEquals(0, buffer.arrayOffset())
        assertEquals(0, buffer.position())
        assertEquals(source.payloads.single().size, buffer.remaining())
        assertArrayEquals(source.payloads.single(), buffer.array())

        // The buffer aliases neither the source payload nor any internal frame state:
        // mutating it cannot change what the source will hand out again.
        assertNotSame(source.payloads.single(), buffer.array())
        buffer.array().fill(0x00)
        assertArrayEquals(opus20msPacket(0x01), source.payloads.single())

        // A second, independent handler still delivers the original bytes.
        val freshHandler = JdaAudioSendHandler(CountingFrameSource(frameCount = 1))
        assertTrue(freshHandler.canProvide())
        val freshBuffer = freshHandler.provide20MsAudio()!!
        assertNotSame(buffer.array(), freshBuffer.array())
        assertArrayEquals(opus20msPacket(0x01), freshBuffer.array())
    }

    @Test
    fun `Given pre-encoded Opus frames When isOpus is queried Then no re-encoding is requested`() {
        // Given
        val handler = JdaAudioSendHandler(CountingFrameSource(frameCount = 1))

        // Then
        assertTrue(handler.isOpus())
    }

    @Test
    fun `Given an exhausted source When canProvide is called again Then it stays false`() {
        // Given
        val source = CountingFrameSource(frameCount = 1)
        val handler = JdaAudioSendHandler(source)
        assertTrue(handler.canProvide())
        handler.provide20MsAudio()

        // When
        val afterExhaustion = handler.canProvide()
        val again = handler.canProvide()

        // Then
        assertFalse(afterExhaustion)
        assertFalse(again)
        assertNull(handler.provide20MsAudio())
        assertEquals(1, handler.framesProvided)
        assertTrue(handler.awaitCompletion(50))
    }

    @Test
    fun `Given a completed playback When awaitCompletion is called Then it returns true immediately`() {
        // Given
        val handler = JdaAudioSendHandler(CountingFrameSource(frameCount = 2))

        // When
        while (handler.canProvide()) {
            handler.provide20MsAudio()
        }

        // Then
        assertTrue(handler.awaitCompletion(50))
        assertEquals(2, handler.framesProvided)
    }

    @Test
    fun `Given a source that fails When canProvide is called Then the handler records the failure`() {
        // Given
        val handler = JdaAudioSendHandler(FailingFrameSource(IllegalStateException("source boom")))

        // When
        val canProvide = handler.canProvide()

        // Then
        assertFalse(canProvide)
        assertTrue(handler.failed)
        assertFalse(handler.awaitCompletion(50))
        assertNull(handler.provide20MsAudio())
        assertEquals(0, handler.framesProvided)
    }

    @Test
    fun `Given a source that fails with a fatal AssertionError When canProvide is called Then the same instance is rethrown`() {
        // Given: an Error is control flow, not best-effort source noise. JDA's send thread must see
        // the exact fatal instance instead of a silent `false` that hides a dying JVM.
        val fatal = AssertionError("fatal source assertion")
        val source = FailingFrameSource(fatal)
        val handler = JdaAudioSendHandler(source)

        // When
        val thrown = assertThrows(AssertionError::class.java) { handler.canProvide() }

        // Then: the fatal failure propagates unchanged and is still recorded, so the session
        // waiter can wake up and run its cleanup before the fatal error keeps propagating.
        assertSame(fatal, thrown, "canProvide must rethrow the exact fatal source failure")
        assertTrue(handler.failed, "the fatal failure must be recorded before it propagates")
        assertNull(handler.provide20MsAudio())
        assertEquals(0, handler.framesProvided)
        assertEquals(1, source.calls.get(), "canProvide must poll the source exactly once")
        assertEquals(0, source.closeCalls.get(), "the handler must not close the caller-owned source")
    }

    @Test
    fun `Given a source that fails with a fatal OutOfMemoryError When the playable waiter runs Then it rethrows the fatal failure`() =
        runBlocking {
            // Given: a fatal source failure reaches the handler before the session starts waiting.
            val fatal = OutOfMemoryError("fatal voice source")
            val source = FailingFrameSource(fatal)
            val handler = JdaAudioSendHandler(source)
            val fromSendThread = assertThrows(OutOfMemoryError::class.java) { handler.canProvide() }
            assertSame(fatal, fromSendThread, "JDA's send thread must receive the exact fatal instance")

            // When: the session coroutine waits for playback completion.
            val fromWaiter = assertFailsWith<OutOfMemoryError> { handler.awaitCompletionCancellable(100) }

            // Then: the waiter rethrows the fatal failure rather than converting it into `false`,
            // so the session can clean up and then let the fatal error keep propagating.
            // kotlinx.coroutines may copy the throwable for stack-trace recovery, so the waiter is
            // checked by its fatal type and message; canProvide above pins the exact instance.
            assertEquals(fatal.message, fromWaiter.message, "the waiter must rethrow the fatal source failure")
            assertTrue(handler.failed)
            assertEquals(1, source.calls.get())
            assertEquals(0, source.closeCalls.get())
        }

    @Test
    fun `Given a source that throws a CancellationException When canProvide is called Then it is rethrown as fatal`() {
        // Given: cancellation from the source is fatal control flow, not a recoverable failure.
        val fatal = CancellationException("fatal source cancellation")
        val source = FailingFrameSource(fatal)
        val handler = JdaAudioSendHandler(source)

        // When
        val thrown = assertThrows(CancellationException::class.java) { handler.canProvide() }

        // Then
        assertSame(fatal, thrown, "canProvide must not downgrade a source cancellation to an ordinary failure")
        assertTrue(handler.failed)
        assertNull(handler.provide20MsAudio())
        assertEquals(0, handler.framesProvided)
        assertEquals(1, source.calls.get())
        assertEquals(0, source.closeCalls.get(), "the handler must not close the caller-owned source")
    }

    @Test
    fun `Given a source that fails with a fatal AssertionError When the blocking waiter runs Then it unwraps the future and rethrows`() {
        // Given: an Error is control flow, not best-effort source noise. The handler records it
        // and then lets it escape JDA's audio thread.
        val fatal = AssertionError("fatal blocking source assertion")
        val source = FailingFrameSource(fatal)
        val handler = JdaAudioSendHandler(source)

        // When: canProvide hits the fatal source failure before any caller starts blocking.
        val fromSendThread = captureThrowable { handler.canProvide() }

        // Then: the fatal failure is signalled for waiters and rethrown unchanged.
        assertSame(fatal, fromSendThread, "canProvide must rethrow the exact fatal source failure")
        assertTrue(handler.failed, "the fatal failure must be recorded before it propagates")

        // When: the blocking waiter observes the already-completed future.
        val fromWaiter = unwrapFutureFailure(captureThrowable { handler.awaitCompletion(1_000) })

        // Then: a fatal source failure must keep its precedence instead of becoming `false`.
        // JDK future wrappers (ExecutionException/CompletionException) are unwrapped first, so the
        // assertion pins the fatal instance itself rather than the wrapper.
        assertSame(
            fatal,
            fromWaiter,
            "awaitCompletion must unwrap the completed future and rethrow the same fatal instance",
        )
        assertEquals(1, source.calls.get(), "the fatal failure must not poll the source again")
        assertEquals(0, source.closeCalls.get(), "the handler must not close the caller-owned source")
    }

    @Test
    fun `Given a source that throws a fatal CancellationException When the blocking waiter runs Then it rethrows the cancellation`() {
        // Given: a cancellation signalled by the source is fatal control flow, not a recoverable failure.
        val fatal = CancellationException("fatal blocking source cancellation")
        val source = FailingFrameSource(fatal)
        val handler = JdaAudioSendHandler(source)

        // When
        val fromSendThread = captureThrowable { handler.canProvide() }

        // Then
        assertSame(fatal, fromSendThread, "canProvide must not downgrade a source cancellation")
        assertTrue(handler.failed, "the fatal failure must be recorded before it propagates")

        // When: the blocking waiter observes the already-completed future.
        val fromWaiter = unwrapFutureFailure(captureThrowable { handler.awaitCompletion(1_000) })

        // Then: the waiter rethrows the cancellation instead of reporting an ordinary failure.
        assertSame(
            fatal,
            fromWaiter,
            "awaitCompletion must rethrow the source cancellation rather than returning false",
        )
        assertEquals(1, source.calls.get())
        assertEquals(0, source.closeCalls.get(), "the handler must not close the caller-owned source")
    }

    @Test
    fun `Given a recoverable source failure When the blocking waiter runs Then it reports an ordinary incomplete result`() {
        // Given: an ordinary exception is a source failure, not fatal control flow.
        val source = FailingFrameSource(IllegalStateException("source boom"))
        val handler = JdaAudioSendHandler(source)
        assertFalse(handler.canProvide())
        assertTrue(handler.failed)

        // When: the blocking waiter observes the already-completed future.
        var completed = true
        val thrown = try {
            completed = handler.awaitCompletion(1_000)
            null
        } catch (error: Throwable) {
            error
        }

        // Then: a recoverable failure is a normal `false` result; it must never surface as an Error.
        assertNull(
            thrown,
            "a recoverable source failure must be reported as a normal result, not thrown as " +
                "${thrown?.javaClass?.name}",
        )
        assertFalse(completed, "a recoverable failure must be reported as an incomplete playback")
        assertNull(handler.provide20MsAudio())
        assertEquals(0, handler.framesProvided)
        assertEquals(1, source.calls.get())
        assertEquals(0, source.closeCalls.get(), "the handler must not close the caller-owned source")
    }

    @Test
    fun `Given a recoverable source failure When the cancellable waiter runs Then it reports an ordinary failure`() =
        runBlocking {
            // Given: an ordinary exception is still a source failure, not fatal control flow.
            val source = FailingFrameSource(IllegalStateException("source boom"))
            val handler = JdaAudioSendHandler(source)
            assertFalse(handler.canProvide())
            assertTrue(handler.failed)

            // When
            val completed = handler.awaitCompletionCancellable(100)

            // Then
            assertFalse(completed, "a recoverable failure must be reported as an incomplete playback")
            assertNull(handler.provide20MsAudio())
            assertEquals(0, handler.framesProvided)
            assertEquals(1, source.calls.get())
            assertEquals(0, source.closeCalls.get(), "the handler must not close the caller-owned source")
        }

    @Test
    fun `Given a source that ends early When awaited Then completion still reports exhaustion`() {
        // Given
        val source = CountingFrameSource(frameCount = 2, expectedCount = 5)
        val handler = JdaAudioSendHandler(source)

        // When
        while (handler.canProvide()) {
            handler.provide20MsAudio()
        }

        // Then
        assertTrue(handler.awaitCompletion(50))
        assertFalse(handler.failed)
        assertEquals(2, handler.framesProvided)
        assertEquals(5, source.expectedFrameCount)
    }

    @Test
    fun `Given the waiting thread is interrupted When awaiting Then return false and preserve the interrupt flag`() {
        // Given
        val handler = JdaAudioSendHandler(CountingFrameSource(frameCount = 1))

        // When
        Thread.currentThread().interrupt()
        try {
            val completed = handler.awaitCompletion(50)

            // Then
            assertFalse(completed)
            assertTrue(Thread.currentThread().isInterrupted)
        } finally {
            Thread.interrupted()
        }
    }

    @Test
    fun `Given a prefetched frame When close runs Then the frame is discarded and the source stays open`() {
        // Given
        val source = CountingFrameSource(frameCount = 1)
        val handler = JdaAudioSendHandler(source)
        assertTrue(handler.canProvide())
        assertEquals(1, source.calls.get())

        // When
        handler.close()

        // Then: the prefetched frame dies with the handler and the source is never consulted again.
        assertNull(handler.provide20MsAudio())
        assertFalse(handler.canProvide())
        assertEquals(1, source.calls.get(), "a closed handler must not read the source again")
        assertEquals(0, handler.framesProvided)
        assertEquals(0, source.closeCalls.get(), "the handler must not close the caller-owned source")
    }

    @Test
    fun `Given a closed handler When the send loop keeps polling Then it never receives audio`() {
        // Given
        val source = CountingFrameSource(frameCount = 1)
        val handler = JdaAudioSendHandler(source)
        handler.close()

        // When
        repeat(3) { assertFalse(handler.canProvide()) }
        repeat(3) { assertNull(handler.provide20MsAudio()) }

        // Then
        assertEquals(0, source.calls.get(), "post-close polling must not touch the source")
        assertEquals(0, handler.framesProvided)
        assertEquals(0, source.closeCalls.get())
        assertFalse(handler.awaitCompletion(50))
    }

    @Test
    fun `Given a closed handler When close is called again Then it is idempotent`() {
        // Given
        val source = CountingFrameSource(frameCount = 1)
        val handler = JdaAudioSendHandler(source)
        handler.close()

        // When / Then: repeated closes must neither throw nor change the post-close state.
        handler.close()
        handler.close()
        assertFalse(handler.awaitCompletion(50))
        assertEquals(0, source.calls.get())
        assertEquals(0, source.closeCalls.get())
    }

    @Test
    fun `Given close before any prefetch When awaited Then the waiter reports a stop not a completion`() {
        // Given
        val handler = JdaAudioSendHandler(CountingFrameSource(frameCount = 2))

        // When
        handler.close()

        // Then
        assertFalse(handler.awaitCompletion(50))
        assertEquals(0, handler.framesProvided)
    }

    @Test
    fun `Given a frame claimed before close When close runs Then the claimed frame stays the only return`() {
        // Given: the frame was claimed by the send loop before close linearized. The expected
        // count stays above one so the close, not the playback, releases the waiters.
        val source = CountingFrameSource(frameCount = 1, expectedCount = 2)
        val handler = JdaAudioSendHandler(source)
        assertTrue(handler.canProvide())
        val claimed = handler.provide20MsAudio()!!

        // When
        handler.close()

        // Then: the already-claimed copy remains valid and finishes exactly once, while no
        // second frame can escape and the waiter observes a stop rather than a completion.
        assertNull(handler.provide20MsAudio())
        assertFalse(handler.canProvide())
        assertEquals(1, handler.framesProvided)
        assertArrayEquals(opus20msPacket(0x01), claimed.array())
        assertEquals(1, source.calls.get())
        assertEquals(0, source.closeCalls.get())
        assertFalse(handler.awaitCompletion(50), "a close is a stop, not a completed playback")
    }

    @Test
    fun `Given providers parked before provide When close has returned Then every released provide yields null`() {
        // Given: a prefetched frame and send-loop threads whose call to provide is strictly
        // ordered after close has returned, using a deterministic park/release barrier.
        val providerCount = 3
        repeat(50) { iteration ->
            val source = CountingFrameSource(frameCount = 1)
            val handler = JdaAudioSendHandler(source)
            assertTrue(handler.canProvide())
            val parked = CountDownLatch(providerCount)
            val release = CountDownLatch(1)
            val results = Array(providerCount) { AtomicReference<ByteBuffer?>() }
            val failure = AtomicReference<Throwable?>()
            val providers = List(providerCount) { index ->
                Thread({
                    try {
                        parked.countDown()
                        release.await()
                        results[index].set(handler.provide20MsAudio())
                    } catch (error: Throwable) {
                        failure.compareAndSet(null, error)
                    }
                }, "audio-provider-$iteration-$index").apply {
                    isDaemon = true
                    start()
                }
            }

            // When: close returns while every provider is still parked before its call.
            assertTrue(parked.await(5, TimeUnit.SECONDS), "iteration $iteration: providers never parked")
            handler.close()
            release.countDown()
            providers.forEach { it.join(5_000) }

            // Then: a call that starts after close returned cannot claim the discarded frame.
            assertNull(failure.get(), "iteration $iteration: a provider threw")
            assertFalse(providers.any { it.isAlive }, "iteration $iteration: a provider did not finish")
            results.forEachIndexed { index, result ->
                assertNull(
                    result.get(),
                    "iteration $iteration: provider $index claimed a frame after close returned",
                )
            }
            assertEquals(0, handler.framesProvided, "iteration $iteration: a post-close provide counted a frame")
            assertEquals(1, source.calls.get(), "iteration $iteration: a post-close provide read the source")
            assertEquals(0, source.closeCalls.get(), "iteration $iteration: the handler closed the caller source")
        }
    }

    @Test
    fun `Given providers racing close When close returns Then no provide claims a buffer afterwards`() {
        // Given: one prefetched frame and several send-loop threads racing an explicit close.
        // A provider may legally finish a claim that linearized before close, so the bounded
        // race records both the non-null returns and whether they landed after close returned.
        val iterations = 200
        val providerCount = 4
        repeat(iterations) { iteration ->
            val source = CountingFrameSource(frameCount = 1)
            val handler = JdaAudioSendHandler(source)
            assertTrue(handler.canProvide())

            val ready = CountDownLatch(providerCount)
            val start = CountDownLatch(1)
            val closeReturned = AtomicBoolean(false)
            val nonNullReturns = AtomicInteger(0)
            val returnsAfterClose = AtomicInteger(0)
            val unexpectedPayloads = AtomicInteger(0)
            val failure = AtomicReference<Throwable?>()
            val providers = List(providerCount) { index ->
                Thread({
                    try {
                        ready.countDown()
                        start.await()
                        val buffer = handler.provide20MsAudio()
                        if (buffer != null) {
                            nonNullReturns.incrementAndGet()
                            if (!buffer.array().contentEquals(opus20msPacket(0x01))) {
                                unexpectedPayloads.incrementAndGet()
                            }
                            if (closeReturned.get()) {
                                returnsAfterClose.incrementAndGet()
                            }
                        }
                    } catch (error: Throwable) {
                        failure.compareAndSet(null, error)
                    }
                }, "audio-racer-$iteration-$index").apply {
                    isDaemon = true
                    start()
                }
            }

            // When: every provider is running before the race, and close completes before the
            // post-close probes below run.
            assertTrue(ready.await(5, TimeUnit.SECONDS), "iteration $iteration: providers never started")
            start.countDown()
            handler.close()
            closeReturned.set(true)
            providers.forEach { it.join(5_000) }

            // Then: at most the single pre-linearized frame escapes, and it escapes only once.
            assertNull(failure.get(), "iteration $iteration: a provider threw")
            assertFalse(providers.any { it.isAlive }, "iteration $iteration: a provider did not finish")
            assertEquals(0, unexpectedPayloads.get(), "iteration $iteration: a provider received foreign bytes")
            assertTrue(
                nonNullReturns.get() <= 1,
                "iteration $iteration: ${nonNullReturns.get()} providers claimed a buffer; " +
                    "close must share the claim critical section",
            )
            if (returnsAfterClose.get() > 0) {
                assertEquals(
                    1,
                    nonNullReturns.get(),
                    "iteration $iteration: a buffer returned after close returned must be the single pre-close claim",
                )
            }
            assertEquals(
                nonNullReturns.get(),
                handler.framesProvided,
                "iteration $iteration: framesProvided must match the buffers actually handed out",
            )
            // Every poll that begins after close returned is dead.
            assertNull(handler.provide20MsAudio(), "iteration $iteration: a new claim survived close")
            assertFalse(handler.canProvide(), "iteration $iteration: canProvide revived a closed handler")
            assertNull(handler.provide20MsAudio(), "iteration $iteration: a repeated poll claimed a frame")
            assertEquals(
                nonNullReturns.get(),
                handler.framesProvided,
                "iteration $iteration: post-close polls grew framesProvided",
            )
            assertEquals(1, source.calls.get(), "iteration $iteration: a post-close poll read the source")
            assertEquals(0, source.closeCalls.get(), "iteration $iteration: the handler closed the caller source")
        }
    }

    @Test
    fun `Given a fatal failure parked before notifying waiters When close races it Then close publishes the same fatal`() =
        runBlocking {
            // Given: the injected completion future parks the first exceptional notification right
            // after recordSourceFailure stored the fatal under stateLock but before it can wake any
            // waiter. That turns the close-versus-failure race into a fixed ordering.
            val fatal = AssertionError("fatal source failure parked before notification")
            val source = FailingFrameSource(fatal)
            val completion = ParkedCompletionFuture()
            val handler = JdaAudioSendHandler(source, completion)
            val audioOutcome = AtomicReference<Throwable?>()

            val audioThread = Thread({
                try {
                    handler.canProvide()
                } catch (error: Throwable) {
                    // The audio thread rethrows the fatal failure after its notification is released.
                    audioOutcome.set(error)
                }
            }, "audio-send-handler-parked-failure").apply {
                isDaemon = true
                start()
            }

            var closePublication: Throwable? = null
            try {
                // When: the audio thread has recorded the fatal but its first
                // completeExceptionally call is still parked inside the future.
                assertTrue(
                    completion.firstSignalParked.await(5, TimeUnit.SECONDS),
                    "recordSourceFailure never reached the completion future",
                )
                assertTrue(handler.failed, "the fatal failure must be recorded before it notifies waiters")

                handler.close()
                closePublication = completion.publications.getOrNull(1)

                // Then: close must adopt the already-recorded fatal instead of stopping the future.
                assertSame(
                    fatal,
                    closePublication,
                    "close must publish the recorded fatal failure, not a StoppedException",
                )
                assertTrue(completion.isCompletedExceptionally, "close must fail the completion future")
            } finally {
                // Release the parked notification even if an assertion above failed, so the audio
                // thread can never outlive the test.
                completion.releaseFirstSignal.countDown()
                audioThread.join(5_000)
            }

            // Then: the original notification is a no-op after close won the future, and the audio
            // thread still rethrows the exact fatal instance it recorded.
            assertFalse(audioThread.isAlive, "the audio thread did not finish after the signal was released")
            assertSame(fatal, audioOutcome.get(), "canProvide must rethrow the exact fatal source failure")
            assertEquals(2, completion.publications.size, "each side must attempt exactly one completion")
            assertSame(fatal, completion.publications[0], "the source must notify the recorded fatal")
            assertSame(fatal, completion.publications[1], "close must notify the very same fatal instance")
            assertEquals(1, source.calls.get(), "the fatal failure must not poll the source again")
            assertEquals(0, source.closeCalls.get(), "the handler must not close the caller-owned source")
            assertEquals(0, handler.framesProvided)

            // Then: both waiters rethrow the fatal instead of returning false or surfacing a stop.
            val blockingFailure = unwrapFutureFailure(captureThrowable { handler.awaitCompletion(1_000) })
            assertSame(fatal, blockingFailure, "awaitCompletion must rethrow the same fatal failure")

            // kotlinx.coroutines may copy the throwable for stack-trace recovery, so the
            // cancellable waiter is pinned by its fatal type and message.
            val cancellableFailure = assertFailsWith<AssertionError> {
                handler.awaitCompletionCancellable(1_000)
            }
            assertEquals(
                fatal.message,
                cancellableFailure.message,
                "awaitCompletionCancellable must rethrow the fatal failure",
            )
        }

    private class CountingFrameSource(
        frameCount: Int,
        expectedCount: Int = frameCount,
    ) : AudioFrameSource {
        val calls = AtomicInteger(0)
        val closeCalls = AtomicInteger(0)
        val payloads = List(frameCount) { index -> opus20msPacket(index + 1) }

        override val format: AudioFrameFormat = AudioFrameFormat.OPUS_20MS
        override val expectedFrameCount: Int = expectedCount

        override fun nextFrame(): AudioFrame? {
            calls.incrementAndGet()
            val index = calls.get() - 1
            return payloads.getOrNull(index)?.let { AudioFrame.of(AudioFrameFormat.OPUS_20MS, it) }
        }

        override fun close() {
            closeCalls.incrementAndGet()
        }
    }

    private class FailingFrameSource(
        private val error: Throwable,
    ) : AudioFrameSource {
        val calls = AtomicInteger(0)
        val closeCalls = AtomicInteger(0)

        override val format: AudioFrameFormat = AudioFrameFormat.OPUS_20MS
        override val expectedFrameCount: Int = 1

        override fun nextFrame(): AudioFrame? {
            calls.incrementAndGet()
            throw error
        }

        override fun close() {
            closeCalls.incrementAndGet()
        }
    }

    /**
     * Fails the future only after the test explicitly releases the first notification.
     *
     * The first [completeExceptionally] call records its argument, parks before the JDK can wake
     * any waiter, and only then delegates to the real implementation. Every later call records its
     * argument and completes immediately, so a racing [JdaAudioSendHandler.close] can publish the
     * fatal failure while the original notification is still parked. The recorded arguments let
     * the test pin the exact instance each side tried to publish instead of inferring it from the
     * future's wrapped cause.
     */
    private class ParkedCompletionFuture : CompletableFuture<Unit>() {
        private val calls = AtomicInteger(0)
        val firstSignalParked = CountDownLatch(1)
        val releaseFirstSignal = CountDownLatch(1)
        val publications = CopyOnWriteArrayList<Throwable>()

        override fun completeExceptionally(ex: Throwable): Boolean {
            publications += ex
            if (calls.incrementAndGet() == 1) {
                firstSignalParked.countDown()
                check(releaseFirstSignal.await(5, TimeUnit.SECONDS)) {
                    "the test never released the parked failure notification"
                }
            }
            return super.completeExceptionally(ex)
        }
    }

    /**
     * Captures whatever [block] throws. When the call returns normally the test fails instead of
     * pretending a throwable was observed.
     */
    private fun captureThrowable(block: () -> Unit): Throwable {
        val thrown = try {
            block()
            null
        } catch (error: Throwable) {
            error
        }
        return thrown ?: fail("Expected the call to throw, but it returned normally")
    }

    /**
     * Unwraps the JDK future wrappers so the test can compare the fatal cause itself. A raw
     * rethrow passes through untouched, and the depth is bounded so a self-referential wrapper
     * cannot hang the test.
     */
    private fun unwrapFutureFailure(thrown: Throwable): Throwable {
        var current = thrown
        var depth = 0
        while (depth < 4 && isFutureWrapper(current) && current.cause != null) {
            current = current.cause!!
            depth++
        }
        return current
    }

    private fun isFutureWrapper(error: Throwable): Boolean =
        error is ExecutionException || error is CompletionException
}

/**
 * Builds a minimal well-formed 20 ms Opus packet: config 15 (CELT 20 ms) with code 0
 * (a single frame). The optional bytes are arbitrary frame data, not an encoding.
 */
private fun opus20msPacket(vararg frameBytes: Int): ByteArray =
    ByteArray(frameBytes.size + 1).also { packet ->
        packet[0] = 0x78
        frameBytes.forEachIndexed { index, value -> packet[index + 1] = value.toByte() }
    }
