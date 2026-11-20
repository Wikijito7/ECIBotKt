package es.wokis.jda.command

import es.wokis.domain.model.UserId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

/**
 * The user-id guard came along with the command-registration move: a value that does not fit JDA's
 * signed 64-bit id must be rejected instead of wrapping to a different member.
 */
class UserIdJdaIdTest {

    @Test
    fun `Given a user id inside the signed range When converted Then keep the exact value`() {
        val userId = UserId(1234567890123456789uL)

        assertEquals(1234567890123456789L, userId.toJdaId())
    }

    @Test
    fun `Given Long MAX VALUE When converted Then map exactly without narrowing`() {
        assertEquals(Long.MAX_VALUE, UserId(Long.MAX_VALUE.toULong()).toJdaId())
    }

    @Test
    fun `Given Long MAX VALUE plus one When converted Then reject with a value-free message`() {
        val userId = UserId(Long.MAX_VALUE.toULong() + 1uL)

        val failure = assertThrows(IllegalArgumentException::class.java) { userId.toJdaId() }

        assertEquals("User id does not fit in JDA's signed 64-bit id range", failure.message)
        assertFalse(failure.message.orEmpty().contains(userId.value.toString()))
    }
}
