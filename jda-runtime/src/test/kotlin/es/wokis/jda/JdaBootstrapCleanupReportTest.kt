package es.wokis.jda

import kotlinx.coroutines.CancellationException
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class JdaBootstrapCleanupReportTest {

    @Test
    fun `Given every step succeeds When cleanup runs Then the report is clean`() {
        val events = mutableListOf<String>()
        val steps = listOf(
            BootstrapCleanupStep("one") { events += "one" },
            BootstrapCleanupStep("two") { events += "two" },
        )

        val report = runBootstrapCleanup(steps)

        assertEquals(listOf("one", "two"), events)
        assertFalse(report.failed)
        assertNull(report.failure)
        assertNull(report.fatalFailure)
    }

    @Test
    fun `Given a failing step When cleanup runs Then every later step is still attempted in order`() {
        val events = mutableListOf<String>()
        val failure = IllegalStateException("voice disconnect failed")
        val steps = listOf(
            BootstrapCleanupStep("one") { events += "one" },
            BootstrapCleanupStep("two") {
                events += "two"
                throw failure
            },
            BootstrapCleanupStep("three") { events += "three" },
        )

        val report = runBootstrapCleanup(steps)

        assertEquals(listOf("one", "two", "three"), events)
        assertTrue(report.failed)
        assertSame(failure, report.failure)
        assertNull(report.fatalFailure)
    }

    @Test
    fun `Given several failures When cleanup runs Then none are thrown and all are attached in order`() {
        val first = IllegalStateException("first")
        val second = IllegalArgumentException("second")
        val steps = listOf(
            BootstrapCleanupStep("first") { throw first },
            BootstrapCleanupStep("second") { throw second },
        )

        val report = assertDoesNotThrow<BootstrapCleanupReport> { runBootstrapCleanup(steps) }

        assertSame(first, report.failure)
        assertNull(report.fatalFailure)
        val primary = RuntimeException("primary startup failure")
        report.attachTo(primary)
        assertEquals(listOf(first, second), primary.suppressed.toList())
    }

    @Test
    fun `Given an Error When cleanup runs Then it is collected as the fatal failure without being thrown`() {
        val error = AssertionError("native error")
        val steps = listOf(
            BootstrapCleanupStep("fatal") { throw error },
            BootstrapCleanupStep("after") { },
        )

        val report = assertDoesNotThrow<BootstrapCleanupReport> { runBootstrapCleanup(steps) }

        assertSame(error, report.failure)
        assertSame(error, report.fatalFailure)
    }

    @Test
    fun `Given a normal failure before a cancellation When cleanup runs Then the cancellation is the fatal failure`() {
        val normal = IllegalStateException("normal")
        val cancellation = CancellationException("cancelled")
        val steps = listOf(
            BootstrapCleanupStep("normal") { throw normal },
            BootstrapCleanupStep("cancel") { throw cancellation },
        )

        val report = runBootstrapCleanup(steps)

        assertSame(normal, report.failure)
        assertSame(cancellation, report.fatalFailure)
    }

    @Test
    fun `Given a primary failure When cleanup failures are attached twice Then each is suppressed once and never itself`() {
        val primary = IllegalStateException("primary")
        val other = IllegalArgumentException("other")
        val report = runBootstrapCleanup(
            listOf(
                BootstrapCleanupStep("primary") { throw primary },
                BootstrapCleanupStep("other") { throw other },
            ),
        )

        report.attachTo(primary)
        report.attachTo(primary)

        assertEquals(listOf(other), primary.suppressed.toList())
    }

    @Test
    fun `Given no startup failure and a clean cleanup When finishing Then exit success`() {
        val exitCode = finishBootstrap(null, BootstrapCleanupReport(emptyList()))

        assertEquals(0, exitCode)
    }

    @Test
    fun `Given a primary startup failure When cleanup succeeds Then preserve the failure and exit failed`() {
        val primary = IllegalStateException("startup failed")

        val exitCode = finishBootstrap(primary, BootstrapCleanupReport(emptyList()))

        assertEquals(1, exitCode)
        assertTrue(primary.suppressed.isEmpty())
    }

    @Test
    fun `Given a primary startup failure When cleanup also fails Then attach every cleanup failure and exit failed`() {
        val primary = IllegalStateException("startup failed")
        val cleanup = IllegalArgumentException("cleanup failed")
        val secondCleanup = RuntimeException("second cleanup failed")

        val exitCode = finishBootstrap(primary, BootstrapCleanupReport(listOf(cleanup, secondCleanup)))

        assertEquals(1, exitCode)
        assertEquals(listOf(cleanup, secondCleanup), primary.suppressed.toList())
    }

    @Test
    fun `Given a fatal primary failure When finishing Then rethrow it with cleanup failures suppressed`() {
        val primary = AssertionError("fatal startup")
        val cleanup = IllegalStateException("cleanup failed")

        val thrown = assertThrows(AssertionError::class.java) {
            finishBootstrap(primary, BootstrapCleanupReport(listOf(cleanup)))
        }

        assertSame(primary, thrown)
        assertEquals(listOf(cleanup), primary.suppressed.toList())
    }

    @Test
    fun `Given a cancellation as the primary failure When finishing Then rethrow the cancellation`() {
        val cancellation = CancellationException("stopping")

        val thrown = assertThrows(CancellationException::class.java) {
            finishBootstrap(cancellation, BootstrapCleanupReport(emptyList()))
        }

        assertSame(cancellation, thrown)
    }

    @Test
    fun `Given a successful startup When cleanup fails Then exit failed with the first failure reported`() {
        val cleanup = IllegalStateException("cleanup failed")
        val secondCleanup = IllegalArgumentException("second cleanup failed")

        val exitCode = finishBootstrap(null, BootstrapCleanupReport(listOf(cleanup, secondCleanup)))

        assertEquals(1, exitCode)
        assertEquals(listOf(secondCleanup), cleanup.suppressed.toList())
    }

    @Test
    fun `Given a successful startup When cleanup fails fatally Then throw the fatal cleanup failure`() {
        val fatal = AssertionError("fatal cleanup")
        val secondary = IllegalStateException("secondary cleanup")

        val thrown = assertThrows(AssertionError::class.java) {
            finishBootstrap(null, BootstrapCleanupReport(listOf(secondary, fatal)))
        }

        assertSame(fatal, thrown)
        assertEquals(listOf(secondary), fatal.suppressed.toList())
    }

    @Test
    fun `Given a normal startup failure When cleanup fails fatally Then the fatal cleanup failure wins with the primary suppressed`() {
        val primary = IllegalStateException("startup failed")
        val fatal = AssertionError("fatal cleanup")

        val thrown = assertThrows(AssertionError::class.java) {
            finishBootstrap(primary, BootstrapCleanupReport(listOf(fatal)))
        }

        assertSame(fatal, thrown)
        assertEquals(listOf(primary), fatal.suppressed.toList())
    }
}
