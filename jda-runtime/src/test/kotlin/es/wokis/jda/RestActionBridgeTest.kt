package es.wokis.jda

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import net.dv8tion.jda.api.requests.RestAction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.function.Consumer

class RestActionBridgeTest {

    @Test
    fun `Given successful action When awaited Then return its value`() = runTest {
        val action = mockk<RestAction<String>>()
        every { action.queue(any(), any()) } answers {
            firstArg<Consumer<String>>().accept("value")
        }

        val result = action.awaitRestAction()

        assertEquals("value", result)
    }

    @Test
    fun `Given successful empty action When completion awaited Then accept a null payload`() = runTest {
        val action = mockk<RestAction<String>>()
        every { action.queue(any(), any()) } answers {
            firstArg<Consumer<String?>>().accept(null)
        }

        action.awaitRestActionCompletion()

        verify(exactly = 1) { action.queue(any(), any()) }
    }

    @Test
    fun `Given failure callback When awaited Then rethrow the REST error`() = runTest {
        val action = mockk<RestAction<String>>()
        val expected = IllegalStateException("rest failed")
        every { action.queue(any(), any()) } answers {
            secondArg<Consumer<Throwable>>().accept(expected)
        }

        val actual = runCatching { action.awaitRestAction() }.exceptionOrNull()

        assertTrue(actual is IllegalStateException)
        assertEquals(expected.message, actual?.message)
    }

    @Test
    fun `Given synchronous queue rejection When awaited Then propagate the error`() = runTest {
        val action = mockk<RestAction<String>>()
        val expected = IllegalStateException("queue rejected")
        every { action.queue(any(), any()) } throws expected

        val actual = runCatching { action.awaitRestAction() }.exceptionOrNull()

        assertTrue(actual is IllegalStateException)
        assertEquals(expected.message, actual?.message)
    }
}
