package es.wokis.player

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class PlayerMessagePermanentFailureTest {

    @Test
    fun `Given only a message When constructed Then keep the concrete type and message`() {
        val failure = PlayerMessagePermanentFailure("Unknown Message")

        assertEquals(PlayerMessagePermanentFailure::class.java, failure.javaClass)
        assertEquals("Unknown Message", failure.message)
        assertNull(failure.cause)
    }

    @Test
    fun `Given an original cause When constructed Then keep it attached`() {
        val cause = IllegalStateException("gone")
        val failure = PlayerMessagePermanentFailure("Unknown Message", cause)

        assertEquals(PlayerMessagePermanentFailure::class.java, failure.javaClass)
        assertEquals("Unknown Message", failure.message)
        assertSame(cause, failure.cause)
    }
}
