package spike.jdadave

import net.dv8tion.jda.api.audio.hooks.ConnectionStatus
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConnectionWaiterTest {

    @Test
    fun `times out with a generic timeout when no terminal status arrived`() {
        val waiter = VoiceTestProbe.ConnectionWaiter()
        waiter.markRequested()

        val thrown = assertFailsWith<TimeoutException> {
            waiter.awaitConnected(1, TimeUnit.MILLISECONDS)
        }

        assertContains(thrown.message.orEmpty(), "did not reach CONNECTED")
        assertNull(waiter.terminalMessage())
    }

    @Test
    fun `authentication failure fails promptly with the authentication reason`() {
        val waiter = VoiceTestProbe.ConnectionWaiter()
        waiter.markRequested()

        val startedAt = System.nanoTime()
        waiter.onStatusChange(ConnectionStatus.DISCONNECTED_AUTHENTICATION_FAILURE)
        val thrown = assertFailsWith<ExecutionException> {
            waiter.awaitConnected(5, TimeUnit.SECONDS)
        }
        val elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000

        assertVoiceProbeFailure(thrown.cause, "DISCONNECTED_AUTHENTICATION_FAILURE")
        assertContains(waiter.terminalMessage().orEmpty(), "authentication")
        assertTrue(elapsedMillis < 2_000, "terminal status took ${elapsedMillis}ms to fail")
    }

    @Test
    fun `audio region change fails promptly with the region reason`() {
        val waiter = VoiceTestProbe.ConnectionWaiter()
        waiter.markRequested()

        val startedAt = System.nanoTime()
        waiter.onStatusChange(ConnectionStatus.AUDIO_REGION_CHANGE)
        val thrown = assertFailsWith<ExecutionException> {
            waiter.awaitConnected(5, TimeUnit.SECONDS)
        }
        val elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000

        assertVoiceProbeFailure(thrown.cause, "AUDIO_REGION_CHANGE")
        assertContains(waiter.terminalMessage().orEmpty(), "AUDIO_REGION_CHANGE")
        assertTrue(elapsedMillis < 2_000, "terminal status took ${elapsedMillis}ms to fail")
    }

    @Test
    fun `NOT_CONNECTED while CONNECTING is an unexpected loss and fails`() {
        val waiter = VoiceTestProbe.ConnectionWaiter()
        waiter.markRequested()
        assertEquals(VoiceTestProbe.ProbeConnectionState.CONNECTING, waiter.connectionState())

        waiter.onStatusChange(ConnectionStatus.NOT_CONNECTED)

        // The desired state must not silently fall back to IDLE/DISCONNECTED: the probe
        // still wanted the connection it had requested.
        assertEquals(VoiceTestProbe.ProbeConnectionState.CONNECTING, waiter.connectionState())
        val thrown = assertFailsWith<ExecutionException> {
            waiter.awaitConnected(1, TimeUnit.MILLISECONDS)
        }
        assertVoiceProbeFailure(thrown.cause, "NOT_CONNECTED")
        assertContains(waiter.terminalMessage().orEmpty(), "closed before it reached CONNECTED")
    }

    @Test
    fun `ERROR status is terminal`() {
        val waiter = VoiceTestProbe.ConnectionWaiter()
        waiter.markRequested()

        waiter.onStatusChange(ConnectionStatus.ERROR_CONNECTION_TIMEOUT)

        val thrown = assertFailsWith<ExecutionException> {
            waiter.awaitConnected(1, TimeUnit.MILLISECONDS)
        }
        assertVoiceProbeFailure(thrown.cause, "ERROR_CONNECTION_TIMEOUT")
    }

    @Test
    fun `SHUTTING_DOWN is terminal`() {
        val waiter = VoiceTestProbe.ConnectionWaiter()
        waiter.markRequested()

        waiter.onStatusChange(ConnectionStatus.SHUTTING_DOWN)

        val thrown = assertFailsWith<ExecutionException> {
            waiter.awaitConnected(1, TimeUnit.MILLISECONDS)
        }
        assertVoiceProbeFailure(thrown.cause, "SHUTTING_DOWN")
    }

    @Test
    fun `the first terminal status wins over later failures`() {
        val waiter = VoiceTestProbe.ConnectionWaiter()
        waiter.markRequested()

        waiter.onStatusChange(ConnectionStatus.DISCONNECTED_AUTHENTICATION_FAILURE)
        waiter.onStatusChange(ConnectionStatus.ERROR_LOST_CONNECTION)

        assertContains(waiter.terminalMessage().orEmpty(), "authentication")

        val thrown = assertFailsWith<VoiceProbeException> { waiter.checkTerminal() }
        assertVoiceProbeFailure(thrown, "DISCONNECTED_AUTHENTICATION_FAILURE")
    }

    @Test
    fun `a terminal status after CONNECTED is reported instead of a false success`() {
        val waiter = VoiceTestProbe.ConnectionWaiter()
        waiter.markRequested()

        waiter.onStatusChange(ConnectionStatus.CONNECTED)
        waiter.awaitConnected(1, TimeUnit.SECONDS)
        waiter.checkTerminal()
        assertNull(waiter.terminalMessage(), "a healthy CONNECTED must not report a failure")

        waiter.onStatusChange(ConnectionStatus.AUDIO_REGION_CHANGE)

        assertVoiceProbeFailure(
            assertFailsWith<VoiceProbeException> { waiter.checkTerminal() },
            "AUDIO_REGION_CHANGE",
        )
    }

    @Test
    fun `local disconnect intent records DISCONNECTING and the following NOT_CONNECTED records DISCONNECTED`() {
        val waiter = VoiceTestProbe.ConnectionWaiter()
        waiter.markRequested()

        waiter.onStatusChange(ConnectionStatus.CONNECTED)
        waiter.awaitConnected(1, TimeUnit.SECONDS)
        assertEquals(VoiceTestProbe.ProbeConnectionState.CONNECTED, waiter.connectionState())

        val request = waiter.expectDisconnect()
        assertEquals(VoiceTestProbe.ProbeConnectionState.DISCONNECTING, waiter.connectionState())

        waiter.onStatusChange(ConnectionStatus.NOT_CONNECTED)
        assertEquals(VoiceTestProbe.ProbeConnectionState.DISCONNECTED, waiter.connectionState())

        // checkTerminal() must not throw for the disconnect cleanup asked for. The recorded
        // state is the requested end state, not a claim about why JDA reported it.
        waiter.checkTerminal()
        assertNull(waiter.terminalMessage())
        assertTrue(request.await(1, TimeUnit.SECONDS))
    }

    @Test
    fun `repeated NOT_CONNECTED after the requested disconnected state stays DISCONNECTED`() {
        val waiter = VoiceTestProbe.ConnectionWaiter()
        waiter.markRequested()
        waiter.onStatusChange(ConnectionStatus.CONNECTED)
        val request = waiter.expectDisconnect()

        waiter.onStatusChange(ConnectionStatus.NOT_CONNECTED)
        waiter.onStatusChange(ConnectionStatus.NOT_CONNECTED)

        assertEquals(VoiceTestProbe.ProbeConnectionState.DISCONNECTED, waiter.connectionState())
        waiter.checkTerminal()
        assertNull(waiter.terminalMessage())
        assertTrue(request.await(1, TimeUnit.SECONDS))
    }

    @Test
    fun `a NOT_CONNECTED while PLAYING is an unexpected loss and fails the run`() {
        val waiter = VoiceTestProbe.ConnectionWaiter()
        waiter.markRequested()
        waiter.onStatusChange(ConnectionStatus.CONNECTED)
        waiter.awaitConnected(1, TimeUnit.SECONDS)
        waiter.markPlaying()
        assertEquals(VoiceTestProbe.ProbeConnectionState.PLAYING, waiter.connectionState())

        waiter.onStatusChange(ConnectionStatus.NOT_CONNECTED)

        // No local disconnect was requested, so this must be terminal and the probe must
        // never be able to report Success from the tone having been handed to JDA.
        assertEquals(VoiceTestProbe.ProbeConnectionState.PLAYING, waiter.connectionState())
        assertContains(waiter.terminalMessage().orEmpty(), "lost after CONNECTED")
        assertVoiceProbeFailure(
            assertFailsWith<VoiceProbeException> { waiter.checkTerminal() },
            "NOT_CONNECTED",
        )
    }

    @Test
    fun `a requested cleanup NOT_CONNECTED does not overwrite the first terminal reason`() {
        val waiter = VoiceTestProbe.ConnectionWaiter()
        waiter.markRequested()

        waiter.onStatusChange(ConnectionStatus.DISCONNECTED_AUTHENTICATION_FAILURE)
        val request = waiter.expectDisconnect()
        waiter.onStatusChange(ConnectionStatus.NOT_CONNECTED)

        assertEquals(VoiceTestProbe.ProbeConnectionState.DISCONNECTED, waiter.connectionState())
        assertContains(waiter.terminalMessage().orEmpty(), "authentication")
        assertTrue(request.await(1, TimeUnit.SECONDS))
        assertVoiceProbeFailure(
            assertFailsWith<VoiceProbeException> { waiter.checkTerminal() },
            "DISCONNECTED_AUTHENTICATION_FAILURE",
        )
    }

    @Test
    fun `a parked CONNECTED callback records its failure before cleanup can record disconnect intent`() {
        assertParkedCallbackSerializesWithCleanup(
            parkedState = VoiceTestProbe.ProbeConnectionState.CONNECTED,
            reachParkedState = { waiter ->
                waiter.markRequested()
                waiter.onStatusChange(ConnectionStatus.CONNECTED)
                waiter.awaitConnected(1, TimeUnit.SECONDS)
            },
        )
    }

    @Test
    fun `a parked PLAYING callback records its failure before cleanup can record disconnect intent`() {
        assertParkedCallbackSerializesWithCleanup(
            parkedState = VoiceTestProbe.ProbeConnectionState.PLAYING,
            reachParkedState = { waiter ->
                waiter.markRequested()
                waiter.onStatusChange(ConnectionStatus.CONNECTED)
                waiter.awaitConnected(1, TimeUnit.SECONDS)
                waiter.markPlaying()
            },
        )
    }

    @Test
    fun `an unexpected NOT_CONNECTED after CONNECTED is terminal`() {
        val waiter = VoiceTestProbe.ConnectionWaiter()
        waiter.markRequested()

        waiter.onStatusChange(ConnectionStatus.CONNECTED)
        waiter.awaitConnected(1, TimeUnit.SECONDS)
        assertEquals(VoiceTestProbe.ProbeConnectionState.CONNECTED, waiter.connectionState())
        waiter.checkTerminal()

        waiter.onStatusChange(ConnectionStatus.NOT_CONNECTED)

        assertEquals(VoiceTestProbe.ProbeConnectionState.CONNECTED, waiter.connectionState())
        assertContains(waiter.terminalMessage().orEmpty(), "lost after CONNECTED")
        val thrown = assertFailsWith<VoiceProbeException> { waiter.checkTerminal() }
        assertContains(thrown.message.orEmpty(), "NOT_CONNECTED")
    }

    @Test
    fun `a terminal status observed around the timeout is still available to the caller`() {
        val waiter = VoiceTestProbe.ConnectionWaiter()
        waiter.markRequested()

        assertFailsWith<TimeoutException> {
            waiter.awaitConnected(1, TimeUnit.MILLISECONDS)
        }
        assertNull(waiter.terminalMessage())

        // The handler inspects terminalMessage() in its TimeoutException catch block, so a
        // status that lands after the deadline must still replace the generic timeout.
        waiter.onStatusChange(ConnectionStatus.AUDIO_REGION_CHANGE)

        assertContains(waiter.terminalMessage().orEmpty(), "AUDIO_REGION_CHANGE")
        assertVoiceProbeFailure(
            assertFailsWith<VoiceProbeException> { waiter.checkTerminal() },
            "AUDIO_REGION_CHANGE",
        )
    }

    @Test
    fun `NOT_CONNECTED in IDLE cannot confirm a later disconnect request`() {
        val waiter = VoiceTestProbe.ConnectionWaiter()

        waiter.onStatusChange(ConnectionStatus.NOT_CONNECTED)

        assertEquals(VoiceTestProbe.ProbeConnectionState.IDLE, waiter.connectionState())
        waiter.checkTerminal()
        assertNull(waiter.terminalMessage())

        // The IDLE status predates the request and must not complete it. The request stays
        // pending until a NOT_CONNECTED is processed while it is the active request.
        val request = waiter.expectDisconnect()
        assertEquals(VoiceTestProbe.ProbeConnectionState.DISCONNECTING, waiter.connectionState())
        assertFalse(request.await(1, TimeUnit.MILLISECONDS))

        waiter.onStatusChange(ConnectionStatus.NOT_CONNECTED)

        assertEquals(VoiceTestProbe.ProbeConnectionState.DISCONNECTED, waiter.connectionState())
        assertTrue(request.await(1, TimeUnit.SECONDS))
        waiter.checkTerminal()
        assertNull(waiter.terminalMessage())
    }

    @Test
    fun `intermediate connecting statuses do not fail the wait`() {
        val waiter = VoiceTestProbe.ConnectionWaiter()
        waiter.markRequested()

        waiter.onStatusChange(ConnectionStatus.CONNECTING_AWAITING_ENDPOINT)
        waiter.onStatusChange(ConnectionStatus.CONNECTING_AWAITING_WEBSOCKET_CONNECT)
        waiter.onStatusChange(ConnectionStatus.CONNECTING_AWAITING_AUTHENTICATION)
        waiter.onStatusChange(ConnectionStatus.CONNECTING_ATTEMPTING_UDP_DISCOVERY)
        waiter.onStatusChange(ConnectionStatus.CONNECTING_AWAITING_READY)

        waiter.checkTerminal()
        assertNull(waiter.terminalMessage())

        // Still waiting: the harmless statuses must not fabricate a success either.
        assertFailsWith<TimeoutException> {
            waiter.awaitConnected(1, TimeUnit.MILLISECONDS)
        }
    }

    @Test
    fun `a request that reached CONNECTED is not pending and its requested disconnect reaches DISCONNECTED`() {
        val waiter = VoiceTestProbe.ConnectionWaiter()
        waiter.markRequested()

        assertTrue(waiter.hasPendingConnectionRequest())

        waiter.onStatusChange(ConnectionStatus.CONNECTED)
        assertEquals(VoiceTestProbe.ProbeConnectionState.CONNECTED, waiter.connectionState())
        assertFalse(waiter.hasPendingConnectionRequest())

        val request = waiter.expectDisconnect()
        waiter.onStatusChange(ConnectionStatus.NOT_CONNECTED)

        // A request that reached CONNECTED is a requested disconnect, not a queued join
        // that cleanup still has to cancel.
        assertFalse(waiter.hasPendingConnectionRequest())
        assertEquals(VoiceTestProbe.ProbeConnectionState.DISCONNECTED, waiter.connectionState())
        waiter.checkTerminal()
        assertNull(waiter.terminalMessage())
        assertTrue(request.await(1, TimeUnit.SECONDS))
    }

    @Test
    fun `a never-connected request stays pending so cleanup can cancel it`() {
        val waiter = VoiceTestProbe.ConnectionWaiter()
        waiter.markRequested()

        waiter.onStatusChange(ConnectionStatus.NOT_CONNECTED)

        // It failed before CONNECTED, so the run never owned a JDA audio session; cleanup
        // must still treat the queued request as pending.
        assertEquals(VoiceTestProbe.ProbeConnectionState.CONNECTING, waiter.connectionState())
        assertTrue(waiter.hasPendingConnectionRequest())
        assertVoiceProbeFailure(
            assertFailsWith<VoiceProbeException> { waiter.checkTerminal() },
            "NOT_CONNECTED",
        )
    }

    @Test
    fun `a fresh disconnect request is acknowledged only after the desired state becomes DISCONNECTED`() {
        val stateAtBarrier = AtomicReference<VoiceTestProbe.ProbeConnectionState?>(null)
        lateinit var waiter: VoiceTestProbe.ConnectionWaiter
        waiter = VoiceTestProbe.ConnectionWaiter { stateAtBarrier.set(waiter.connectionState()) }
        waiter.markRequested()
        waiter.onStatusChange(ConnectionStatus.CONNECTED)
        waiter.awaitConnected(1, TimeUnit.SECONDS)

        val request = waiter.expectDisconnect()
        assertFalse(request.await(1, TimeUnit.MILLISECONDS))
        stateAtBarrier.set(null)

        waiter.onStatusChange(ConnectionStatus.NOT_CONNECTED)

        // The barrier runs after the entry snapshot, before the callback's decision. It must
        // still see DISCONNECTING: the acknowledgement happens only after the transition to
        // DISCONNECTED, never before it.
        assertEquals(
            VoiceTestProbe.ProbeConnectionState.DISCONNECTING,
            stateAtBarrier.get(),
            "the callback must see DISCONNECTING at entry and acknowledge only after the transition",
        )
        assertEquals(VoiceTestProbe.ProbeConnectionState.DISCONNECTED, waiter.connectionState())
        assertTrue(request.await(1, TimeUnit.SECONDS))
    }

    @Test
    fun `a callback acknowledges only its snapshotted request and the next request waits for it to release`() {
        val barrier = PausingNotConnectedBarrier()
        val waiter = VoiceTestProbe.ConnectionWaiter(barrier)
        waiter.markRequested()
        waiter.onStatusChange(ConnectionStatus.CONNECTED)
        waiter.awaitConnected(1, TimeUnit.SECONDS)

        val first = waiter.expectDisconnect()
        val callbackFailure = AtomicReference<Throwable?>(null)
        val callback = Thread {
            try {
                waiter.onStatusChange(ConnectionStatus.NOT_CONNECTED)
            } catch (t: Throwable) {
                callbackFailure.set(t)
            }
        }

        // The callback snapshotted the first request and holds the state lock. A concurrent
        // expectDisconnect() must wait for that whole callback decision to finish; it cannot
        // replace the request the callback is about to acknowledge.
        val secondRef = AtomicReference<VoiceTestProbe.ConnectionWaiter.DisconnectRequest?>(null)
        val secondReturned = CountDownLatch(1)
        val second = Thread {
            secondRef.set(waiter.expectDisconnect())
            secondReturned.countDown()
        }

        callback.start()
        try {
            assertTrue(barrier.entered.await(5, TimeUnit.SECONDS), "callback did not reach the barrier")

            second.start()
            try {
                assertFalse(
                    secondReturned.await(200, TimeUnit.MILLISECONDS),
                    "a concurrent expectDisconnect() must wait for the callback to release the state lock",
                )
                assertEquals(VoiceTestProbe.ProbeConnectionState.DISCONNECTING, waiter.connectionState())
                assertNull(secondRef.get(), "no second request may exist while the callback is parked")
                assertNull(waiter.terminalMessage(), "the requested disconnect must not be a terminal failure")
            } finally {
                barrier.release.countDown()
            }

            joinAllBounded(callback, second)
            assertFalse(callback.isAlive, "callback thread did not finish")
            assertFalse(second.isAlive, "second expectDisconnect() thread did not finish")
            assertNull(callbackFailure.get(), "the callback itself must not throw")

            // The callback acknowledged the request it snapshotted, and only afterwards could the
            // second request replace it.
            assertTrue(first.await(1, TimeUnit.SECONDS), "the callback must acknowledge its own request")
            val secondRequest = checkNotNull(secondRef.get()) { "expectDisconnect() did not return a request" }
            assertFalse(
                secondRequest.await(1, TimeUnit.MILLISECONDS),
                "the callback that snapshotted the first request must not confirm the second",
            )
            assertEquals(VoiceTestProbe.ProbeConnectionState.DISCONNECTING, waiter.connectionState())

            // Only a callback that enters while the second request is the active one can
            // acknowledge it; a later duplicate is ignored.
            waiter.onStatusChange(ConnectionStatus.NOT_CONNECTED)
            assertTrue(secondRequest.await(1, TimeUnit.SECONDS))
            assertEquals(VoiceTestProbe.ProbeConnectionState.DISCONNECTED, waiter.connectionState())

            waiter.onStatusChange(ConnectionStatus.NOT_CONNECTED)
            assertEquals(VoiceTestProbe.ProbeConnectionState.DISCONNECTED, waiter.connectionState())
            assertTrue(secondRequest.await(1, TimeUnit.SECONDS))
            waiter.checkTerminal()
            assertNull(waiter.terminalMessage())
        } finally {
            // Release before joining on every path: a failed await or assertion must not leave
            // the parked callback or the second expectDisconnect() thread blocked in the barrier.
            barrier.release.countDown()
            joinAllBounded(callback, second)
        }
    }

    @Test
    fun `a duplicate NOT_CONNECTED after a completed request does not pre-confirm the next request`() {
        val waiter = VoiceTestProbe.ConnectionWaiter()
        waiter.markRequested()
        waiter.onStatusChange(ConnectionStatus.CONNECTED)
        waiter.awaitConnected(1, TimeUnit.SECONDS)

        val first = waiter.expectDisconnect()
        waiter.onStatusChange(ConnectionStatus.NOT_CONNECTED)
        assertTrue(first.await(1, TimeUnit.SECONDS))
        assertEquals(VoiceTestProbe.ProbeConnectionState.DISCONNECTED, waiter.connectionState())

        // A duplicate status while DISCONNECTED must not become a queued acknowledgement
        // that the next request can consume.
        waiter.onStatusChange(ConnectionStatus.NOT_CONNECTED)
        assertEquals(VoiceTestProbe.ProbeConnectionState.DISCONNECTED, waiter.connectionState())

        val second = waiter.expectDisconnect()
        assertEquals(VoiceTestProbe.ProbeConnectionState.DISCONNECTING, waiter.connectionState())
        assertFalse(second.await(1, TimeUnit.MILLISECONDS))

        waiter.onStatusChange(ConnectionStatus.NOT_CONNECTED)
        assertTrue(second.await(1, TimeUnit.SECONDS))
        assertEquals(VoiceTestProbe.ProbeConnectionState.DISCONNECTED, waiter.connectionState())
    }

    @Test
    fun `a disconnect request reports a timeout when no NOT_CONNECTED arrives`() {
        val waiter = VoiceTestProbe.ConnectionWaiter()

        val request = waiter.expectDisconnect()

        assertFalse(request.await(1, TimeUnit.MILLISECONDS))
    }

    /**
     * Parks an unexpected NOT_CONNECTED callback after it snapshotted [parkedState]. The
     * production callback holds the waiter state lock for its whole decision, so a concurrent
     * `expectDisconnect()` must block until the callback has recorded its terminal failure and
     * released the lock. The request created afterwards therefore cannot be acknowledged by
     * the stale callback, and the run can never be reported as Success.
     *
     * No waiter method that takes the state lock is called from inside the barrier; the
     * lock-taking call runs on [cleanup].
     */
    private fun assertParkedCallbackSerializesWithCleanup(
        parkedState: VoiceTestProbe.ProbeConnectionState,
        reachParkedState: (VoiceTestProbe.ConnectionWaiter) -> Unit,
    ) {
        val barrier = PausingNotConnectedBarrier()
        val waiter = VoiceTestProbe.ConnectionWaiter(barrier)
        reachParkedState(waiter)
        assertEquals(parkedState, waiter.connectionState())

        val callbackFailure = AtomicReference<Throwable?>(null)
        val callback = Thread {
            try {
                waiter.onStatusChange(ConnectionStatus.NOT_CONNECTED)
            } catch (t: Throwable) {
                callbackFailure.set(t)
            }
        }

        val requestRef = AtomicReference<VoiceTestProbe.ConnectionWaiter.DisconnectRequest?>(null)
        val terminalAtTransition = AtomicReference<String?>(null)
        val cleanupReturned = CountDownLatch(1)
        val cleanup = Thread {
            requestRef.set(waiter.expectDisconnect())
            terminalAtTransition.set(waiter.terminalMessage())
            cleanupReturned.countDown()
        }

        callback.start()
        try {
            assertTrue(barrier.entered.await(5, TimeUnit.SECONDS), "callback did not reach the barrier")

            cleanup.start()
            try {
                assertFalse(
                    cleanupReturned.await(200, TimeUnit.MILLISECONDS),
                    "expectDisconnect() must block while the callback holds the waiter state lock",
                )
                // The callback is still parked, so neither intent nor failure has been touched.
                assertEquals(parkedState, waiter.connectionState())
                assertNull(waiter.terminalMessage())
            } finally {
                barrier.release.countDown()
            }

            joinAllBounded(callback, cleanup)
            assertFalse(callback.isAlive, "callback thread did not finish")
            assertFalse(cleanup.isAlive, "expectDisconnect() thread did not finish")
            assertNull(callbackFailure.get(), "the callback itself must not throw")

            // The failure was already recorded when the cleanup transition became visible. Cleanup
            // can therefore never observe a stale "no failure" and let the run succeed.
            assertContains(terminalAtTransition.get().orEmpty(), "lost after CONNECTED")
            assertEquals(VoiceTestProbe.ProbeConnectionState.DISCONNECTING, waiter.connectionState())
            assertContains(waiter.terminalMessage().orEmpty(), "lost after CONNECTED")
            assertVoiceProbeFailure(
                assertFailsWith<VoiceProbeException> { waiter.checkTerminal() },
                "NOT_CONNECTED",
            )

            // The callback snapshotted CONNECTED/PLAYING, classified an unexpected loss, and so
            // never acknowledged the request created after it released the lock.
            val request = checkNotNull(requestRef.get()) { "expectDisconnect() did not return a request" }
            assertFalse(
                request.await(1, TimeUnit.MILLISECONDS),
                "a callback that snapshotted CONNECTED/PLAYING must not confirm a later request",
            )
        } finally {
            // A failed await or assertion must never leave the parked callback or the cleanup
            // worker blocked: release the barrier and join every spawned thread on every path.
            barrier.release.countDown()
            joinAllBounded(callback, cleanup)
        }
    }

    /**
     * Pauses the first NOT_CONNECTED callback after the production callback has snapshotted
     * its state. This is deterministic: the test changes intent only while the callback is
     * provably parked after the entry snapshot, with no sleeping or logger-monitor tricks.
     *
     * The barrier runs while the waiter state lock is held, so it must never call a waiter
     * method that acquires that lock.
     */
    private class PausingNotConnectedBarrier :
        VoiceTestProbe.ConnectionWaiter.StatusCallbackBarrier {
        private val paused = AtomicBoolean(false)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)

        override fun afterEntrySnapshot(status: ConnectionStatus) {
            if (status == ConnectionStatus.NOT_CONNECTED && paused.compareAndSet(false, true)) {
                entered.countDown()
                check(release.await(5, TimeUnit.SECONDS)) {
                    "the test did not release the NOT_CONNECTED callback barrier"
                }
            }
        }
    }

    /**
     * Joins every spawned thread with a bounded wait. A thread that is still alive after the
     * bound is a test failure, so a failed assertion cannot leak a parked callback into the
     * next test or leave it blocked for the barrier's full deadline.
     */
    private fun joinAllBounded(vararg threads: Thread) {
        threads.forEach { it.join(THREAD_JOIN_TIMEOUT_MILLIS) }
        val alive = threads.filter { it.isAlive }
        assertTrue(
            alive.isEmpty(),
            "threads did not finish within ${THREAD_JOIN_TIMEOUT_MILLIS}ms: " +
                alive.joinToString { it.name },
        )
    }

    private fun assertVoiceProbeFailure(throwable: Throwable?, fragment: String): VoiceProbeException {
        val failure = assertIs<VoiceProbeException>(throwable)
        assertContains(failure.message.orEmpty(), fragment)
        return failure
    }

    private companion object {
        private const val THREAD_JOIN_TIMEOUT_MILLIS = 5_000L
    }
}
