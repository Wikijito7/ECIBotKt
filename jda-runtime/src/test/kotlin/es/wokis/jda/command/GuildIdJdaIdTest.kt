package es.wokis.jda.command

import es.wokis.domain.model.GuildId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class GuildIdJdaIdTest {

    @Test
    fun `Given a guild id inside the signed range When converted Then keep the exact value`() {
        // Given
        val guildId = GuildId(1234567890123456789uL)

        // When
        val jdaId = guildId.toJdaId()

        // Then
        assertEquals(1234567890123456789L, jdaId)
    }

    @Test
    fun `Given zero When converted Then return zero`() {
        // When
        val jdaId = GuildId(0uL).toJdaId()

        // Then
        assertEquals(0L, jdaId)
    }

    @Test
    fun `Given Long MAX VALUE When converted Then map exactly without narrowing`() {
        // Given
        val guildId = GuildId(Long.MAX_VALUE.toULong())

        // When
        val jdaId = guildId.toJdaId()

        // Then
        assertEquals(Long.MAX_VALUE, jdaId)
    }

    @Test
    fun `Given Long MAX VALUE plus one When converted Then reject before narrowing`() {
        // Given
        val guildId = GuildId(Long.MAX_VALUE.toULong() + 1uL)

        // Then
        assertThrows(IllegalArgumentException::class.java) { guildId.toJdaId() }
    }

    @Test
    fun `Given ULong MAX VALUE When converted Then reject before narrowing`() {
        // Given
        val guildId = GuildId(ULong.MAX_VALUE)

        // Then
        assertThrows(IllegalArgumentException::class.java) { guildId.toJdaId() }
    }
}
