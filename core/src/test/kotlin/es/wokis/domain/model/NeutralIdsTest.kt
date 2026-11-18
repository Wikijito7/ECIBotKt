package es.wokis.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class NeutralIdsTest {

    @Test
    fun `Given decimal guild id When fromDecimal Then keep exact value`() {
        val guildId = GuildId.fromDecimal("1234567890123456789")

        assertEquals(GuildId(1234567890123456789uL), guildId)
        assertEquals("1234567890123456789", guildId.toString())
    }

    @Test
    fun `Given maximum ULong guild id When represented Then keep full unsigned range`() {
        val expected = "18446744073709551615"

        val guildId = GuildId.fromDecimal(expected)

        assertEquals(GuildId(ULong.MAX_VALUE), guildId)
        assertEquals(expected, guildId.toString())
    }

    @Test
    fun `Given negative long bits When converted to guild id Then preserve unsigned two's complement value`() {
        val guildId = GuildId((-1L).toULong())

        assertEquals(GuildId(ULong.MAX_VALUE), guildId)
        assertEquals("18446744073709551615", guildId.toString())
    }

    @Test
    fun `Given invalid decimal guild id When fromDecimal Then fail instead of defaulting`() {
        assertThrows(NumberFormatException::class.java) { GuildId.fromDecimal("not-a-snowflake") }
        assertThrows(NumberFormatException::class.java) { GuildId.fromDecimal("-1") }
        assertThrows(NumberFormatException::class.java) { GuildId.fromDecimal("18446744073709551616") }
    }

    @Test
    fun `Given equal guild ids When compared Then use value equality`() {
        assertEquals(GuildId(42uL), GuildId(42uL))
        assertNotEquals(GuildId(42uL), GuildId(43uL))
    }

    @Test
    fun `Given decimal channel id When fromDecimal Then keep exact value`() {
        val channelId = ChannelId.fromDecimal("9876543210987654321")

        assertEquals(ChannelId(9876543210987654321uL), channelId)
        assertEquals("9876543210987654321", channelId.toString())
    }

    @Test
    fun `Given invalid decimal channel id When fromDecimal Then fail instead of defaulting`() {
        assertThrows(NumberFormatException::class.java) { ChannelId.fromDecimal("channel") }
    }

    @Test
    fun `Given decimal user id When fromDecimal Then keep exact value`() {
        val userId = UserId.fromDecimal("1112223334445556667")

        assertEquals(UserId(1112223334445556667uL), userId)
        assertEquals("1112223334445556667", userId.toString())
    }

    @Test
    fun `Given invalid decimal user id When fromDecimal Then fail instead of defaulting`() {
        assertThrows(NumberFormatException::class.java) { UserId.fromDecimal("user") }
    }
}
