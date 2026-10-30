package spike.jdadave

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProbeRunTest {

    @Test
    fun `a successful worker result finishes the run exactly once`() {
        val outcomes = mutableListOf<ProbeOutcome>()
        val run = ProbeRun { outcomes += it }

        run.finish(ProbeOutcome.Success)
        run.finish(ProbeOutcome.Failure("late error callback"))

        assertEquals(1, outcomes.size)
        assertEquals(ProbeOutcome.Success, outcomes.single())
    }

    @Test
    fun `an error callback keeps its result when the worker reports later`() {
        val outcomes = mutableListOf<ProbeOutcome>()
        val run = ProbeRun { outcomes += it }
        val error = ProbeOutcome.Failure("Discord did not acknowledge /voice-test.")

        run.finish(error)
        run.finish(ProbeOutcome.Success)

        assertEquals(1, outcomes.size)
        assertEquals(error, outcomes.single())
    }

    @Test
    fun `repeated failure outcomes still notify only once`() {
        val outcomes = mutableListOf<ProbeOutcome>()
        val run = ProbeRun { outcomes += it }

        repeat(5) { attempt ->
            run.finish(ProbeOutcome.Failure("failure-$attempt"))
        }

        assertEquals(1, outcomes.size)
        assertEquals(ProbeOutcome.Failure("failure-0"), outcomes.single())
    }

    @Test
    fun `concurrent worker and error finishes complete exactly once`() {
        val executor = Executors.newFixedThreadPool(2)
        val raceOutcome = ProbeOutcome.Failure("racing error callback")
        try {
            repeat(100) { iteration ->
                val outcomes = CopyOnWriteArrayList<ProbeOutcome>()
                val run = ProbeRun { outcomes += it }
                val gate = CyclicBarrier(2)

                val worker = executor.submit {
                    gate.await()
                    run.finish(ProbeOutcome.Success)
                }
                val error = executor.submit {
                    gate.await()
                    run.finish(raceOutcome)
                }

                try {
                    worker.get(5, TimeUnit.SECONDS)
                    error.get(5, TimeUnit.SECONDS)

                    assertEquals(1, outcomes.size, "iteration $iteration reported ${outcomes.size} outcomes")
                    val winner = outcomes.single()
                    assertTrue(
                        winner == ProbeOutcome.Success || winner == raceOutcome,
                        "iteration $iteration produced an unexpected outcome $winner",
                    )
                } finally {
                    // If a get times out or an assertion fails, a worker may still be parked at
                    // the barrier. Interrupt it so it cannot leak into the next iteration.
                    worker.cancel(true)
                    error.cancel(true)
                }
            }
        } finally {
            executor.shutdownNow()
            assertTrue(
                executor.awaitTermination(THREAD_TERMINATION_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                "executor threads did not finish within ${THREAD_TERMINATION_TIMEOUT_SECONDS}s",
            )
        }
    }

    private companion object {
        private const val THREAD_TERMINATION_TIMEOUT_SECONDS = 5L
    }
}
