package es.wokis.jda.command

import es.wokis.domain.model.GuildId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class GuildIdJdaIdTest {

    @Test
    fun `Given a guild id inside the signed range When converted Then keep the exact value`() {
        val guildId = GuildId(1234567890123456789uL)

        val jdaId = guildId.toJdaId()

        assertEquals(1234567890123456789L, jdaId)
    }

    @Test
    fun `Given zero When converted Then return zero`() {
        val jdaId = GuildId(0uL).toJdaId()

        assertEquals(0L, jdaId)
    }

    @Test
    fun `Given Long MAX VALUE When converted Then map exactly without narrowing`() {
        val guildId = GuildId(Long.MAX_VALUE.toULong())

        val jdaId = guildId.toJdaId()

        assertEquals(Long.MAX_VALUE, jdaId)
    }

    @Test
    fun `Given Long MAX VALUE plus one When converted Then reject before narrowing`() {
        val guildId = GuildId(Long.MAX_VALUE.toULong() + 1uL)

        val failure = assertThrows(IllegalArgumentException::class.java) { guildId.toJdaId() }

        // Then: the guard stays, and the message is generic instead of echoing the snowflake.
        assertEquals("Guild id does not fit in JDA's signed 64-bit id range", failure.message)
        assertFalse(failure.message.orEmpty().contains(guildId.value.toString()))
    }

    @Test
    fun `Given ULong MAX VALUE When converted Then reject before narrowing`() {
        val guildId = GuildId(ULong.MAX_VALUE)

        val failure = assertThrows(IllegalArgumentException::class.java) { guildId.toJdaId() }

        // Then: the guard stays, and the message is generic instead of echoing the snowflake.
        assertEquals("Guild id does not fit in JDA's signed 64-bit id range", failure.message)
        assertFalse(failure.message.orEmpty().contains(guildId.value.toString()))
    }
}
