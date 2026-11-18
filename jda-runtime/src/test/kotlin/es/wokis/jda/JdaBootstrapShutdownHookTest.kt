package es.wokis.jda

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class JdaBootstrapShutdownHookTest {

    @Test
    fun `Given the JVM is already shutting down When the hook is removed Then the IllegalStateException is benign`() {
        // The message below is representative of the current JDK wording, not a documented contract.
        val runtime = mockk<Runtime>()
        val hook = Thread({ }, "test-shutdown-hook")
        val shutdownInProgress = IllegalStateException("Shutdown in progress")
        mockkStatic(Runtime::class)
        try {
            every { Runtime.getRuntime() } returns runtime
            every { runtime.removeShutdownHook(hook) } throws shutdownInProgress

            assertDoesNotThrow { removeShutdownHook(hook) }

            verify(exactly = 1) { runtime.removeShutdownHook(hook) }
        } finally {
            unmockkStatic(Runtime::class)
        }
    }

    @Test
    fun `Given a shutdown-in-progress IllegalStateException with an arbitrary message When the hook is removed Then it is benign`() {
        // Any documented IllegalStateException is benign, so the message must not be matched.
        val runtime = mockk<Runtime>()
        val hook = Thread({ }, "test-shutdown-hook")
        val arbitrary = IllegalStateException("Runtime is shutting down, hooks are frozen")
        mockkStatic(Runtime::class)
        try {
            every { Runtime.getRuntime() } returns runtime
            every { runtime.removeShutdownHook(hook) } throws arbitrary

            assertDoesNotThrow { removeShutdownHook(hook) }

            verify(exactly = 1) { runtime.removeShutdownHook(hook) }
        } finally {
            unmockkStatic(Runtime::class)
        }
    }

    @Test
    fun `Given a shutdown-in-progress IllegalStateException without a message When the hook is removed Then it is benign`() {
        val runtime = mockk<Runtime>()
        val hook = Thread({ }, "test-shutdown-hook")
        val withoutMessage = IllegalStateException()
        mockkStatic(Runtime::class)
        try {
            every { Runtime.getRuntime() } returns runtime
            every { runtime.removeShutdownHook(hook) } throws withoutMessage

            assertDoesNotThrow { removeShutdownHook(hook) }

            verify(exactly = 1) { runtime.removeShutdownHook(hook) }
        } finally {
            unmockkStatic(Runtime::class)
        }
    }

    @Test
    fun `Given a SecurityException When the hook is removed Then it is surfaced`() {
        // Only IllegalStateException is benign; a security failure is a real cleanup failure.
        val runtime = mockk<Runtime>()
        val hook = Thread({ }, "test-shutdown-hook")
        val securityFailure = SecurityException("hook removal is not allowed")
        mockkStatic(Runtime::class)
        try {
            every { Runtime.getRuntime() } returns runtime
            every { runtime.removeShutdownHook(hook) } throws securityFailure

            val thrown = assertThrows(SecurityException::class.java) { removeShutdownHook(hook) }

            assertSame(securityFailure, thrown)
            verify(exactly = 1) { runtime.removeShutdownHook(hook) }
        } finally {
            unmockkStatic(Runtime::class)
        }
    }

    @Test
    fun `Given another runtime exception When the hook is removed Then it is surfaced`() {
        val runtime = mockk<Runtime>()
        val hook = Thread({ }, "test-shutdown-hook")
        val unrelatedFailure = NullPointerException("hook is not registered")
        mockkStatic(Runtime::class)
        try {
            every { Runtime.getRuntime() } returns runtime
            every { runtime.removeShutdownHook(hook) } throws unrelatedFailure

            val thrown = assertThrows(NullPointerException::class.java) { removeShutdownHook(hook) }

            assertSame(unrelatedFailure, thrown)
            verify(exactly = 1) { runtime.removeShutdownHook(hook) }
        } finally {
            unmockkStatic(Runtime::class)
        }
    }

    @Test
    fun `Given an Error When the hook is removed Then it is surfaced`() {
        val runtime = mockk<Runtime>()
        val hook = Thread({ }, "test-shutdown-hook")
        val error = AssertionError("hook removal failed fatally")
        mockkStatic(Runtime::class)
        try {
            every { Runtime.getRuntime() } returns runtime
            every { runtime.removeShutdownHook(hook) } throws error

            val thrown = assertThrows(AssertionError::class.java) { removeShutdownHook(hook) }

            assertSame(error, thrown)
            verify(exactly = 1) { runtime.removeShutdownHook(hook) }
        } finally {
            unmockkStatic(Runtime::class)
        }
    }
}
