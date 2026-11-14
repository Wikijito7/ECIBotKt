package utils

import dev.kord.common.Locale
import dev.kord.common.entity.Snowflake
import es.wokis.domain.locale.DiscordLocaleCatalog
import es.wokis.domain.locale.LocaleCode
import es.wokis.domain.model.GuildId
import es.wokis.utils.DISCORD_LOCALE_MAP
import es.wokis.utils.orDefaultLocale
import es.wokis.utils.toGuildId
import es.wokis.utils.toKordLocaleOrNull
import es.wokis.utils.toLocaleCode
import es.wokis.utils.toLocaleCodeOrNull
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LocaleExtensionsTest {

    @Test
    fun `Given locale When orDefaultLocale is called Then return current locale`() {
        // Given
        val expected = Locale.THAI

        // When
        val actual = expected.orDefaultLocale()

        // Then
        assertEquals(expected, actual)
    }

    @Test
    fun `Given null locale When orDefaultLocale is called Then return US English`() {
        // Given
        val locale: Locale? = null
        val expected = Locale.ENGLISH_UNITED_STATES

        // When
        val actual = locale.orDefaultLocale()

        // Then
        assertEquals(expected, actual)
    }

    @Test
    fun `Given snowflake When toGuildId is called Then preserve bits`() {
        // Given
        val snowflake = Snowflake(123456789uL)

        // When
        val guildId = snowflake.toGuildId()

        // Then
        assertEquals(GuildId(123456789uL), guildId)
        assertEquals(snowflake.value, guildId.value)
    }

    @Test
    fun `Given snowflake above Kord supported range When created Then clamp to max signed snowflake`() {
        // Given
        val rawValue = ULong.MAX_VALUE
        val kordMaxSupportedValue = Long.MAX_VALUE.toULong()

        // When
        val snowflake = Snowflake(rawValue)

        // Then
        assertEquals(kordMaxSupportedValue, Snowflake.validValues.last)
        assertEquals(kordMaxSupportedValue, snowflake.value)
        assertTrue(snowflake.value < rawValue)
    }

    @Test
    fun `Given snowflake above Kord supported range When toGuildId is called Then yield clamped supported value`() {
        // Given
        val snowflake = Snowflake(ULong.MAX_VALUE)

        // When
        val guildId = snowflake.toGuildId()

        // Then
        assertEquals(GuildId(Long.MAX_VALUE.toULong()), guildId)
        assertEquals(Long.MAX_VALUE.toULong(), guildId.value)
        assertEquals(snowflake.value, guildId.value)
    }

    @Test
    fun `Given max supported snowflake When toGuildId is called Then preserve exact value`() {
        // Given
        val maxSupportedSnowflake = Snowflake(Long.MAX_VALUE.toULong())

        // When
        val guildId = maxSupportedSnowflake.toGuildId()

        // Then
        assertEquals(GuildId(Long.MAX_VALUE.toULong()), guildId)
        assertEquals(Long.MAX_VALUE.toULong(), guildId.value)
    }

    @Test
    fun `Given zero snowflake When toGuildId is called Then keep zero`() {
        // Given
        val snowflake = Snowflake(0uL)

        // When
        val guildId = snowflake.toGuildId()

        // Then
        assertEquals(GuildId(0uL), guildId)
        assertEquals(0uL, guildId.value)
    }

    @Test
    fun `Given known locale When toLocaleCode is called Then return exact Discord code`() {
        // Given
        val locale = Locale.SPANISH_SPAIN

        // When
        val localeCode = locale.toLocaleCode()

        // Then
        assertEquals(LocaleCode("es-ES"), localeCode)
        assertEquals("es-ES", localeCode.value)
    }

    @Test
    fun `Given known locale code When toKordLocaleOrNull is called Then return matching locale`() {
        // Given
        val localeCode = LocaleCode("es-ES")

        // When
        val locale = localeCode.toKordLocaleOrNull()

        // Then
        assertEquals(Locale.SPANISH_SPAIN, locale)
    }

    @Test
    fun `Given invalid locale code When toKordLocaleOrNull is called Then return null`() {
        // Given
        val localeCode = LocaleCode("not-a-locale")

        // When
        val locale = localeCode.toKordLocaleOrNull()

        // Then
        assertNull(locale)
    }

    @Test
    fun `Given invalid locale code When falling back to default locale Then return US English`() {
        // Given
        val localeCode = LocaleCode("not-a-locale")

        // When
        val locale = localeCode.toKordLocaleOrNull().orDefaultLocale()

        // Then
        assertEquals(Locale.ENGLISH_UNITED_STATES, locale)
    }

    @Test
    fun `Given valid locale code When falling back to default locale Then return that locale`() {
        // Given
        val localeCode = LocaleCode("fr")

        // When
        val locale = localeCode.toKordLocaleOrNull().orDefaultLocale()

        // Then
        assertEquals(Locale.FRENCH, locale)
    }

    @Test
    fun `Given Discord locale map When queried Then contain known locale codes`() {
        // Given / When / Then
        assertEquals(Locale.SPANISH_SPAIN, DISCORD_LOCALE_MAP["es-ES"])
        assertEquals(Locale.ENGLISH_UNITED_STATES, DISCORD_LOCALE_MAP["en-US"])
        assertEquals(Locale.FRENCH, DISCORD_LOCALE_MAP["fr"])
        assertTrue(DISCORD_LOCALE_MAP.isNotEmpty())
        assertFalse(DISCORD_LOCALE_MAP.containsKey("not-a-locale"))
        assertTrue(Locale.ALL.all { DISCORD_LOCALE_MAP.values.contains(it) })
    }

    @Test
    fun `Given the neutral catalog When the Kord map is built Then it contains exactly every canonical tag`() {
        // Then
        assertEquals(
            DiscordLocaleCatalog.supportedLocales.map { it.value }.toSet(),
            DISCORD_LOCALE_MAP.keys,
        )
        assertEquals(34, DISCORD_LOCALE_MAP.size)
        assertFalse(DISCORD_LOCALE_MAP.containsKey("unknown"))
        assertFalse(DISCORD_LOCALE_MAP.containsKey("UNKNOWN"))
    }

    @Test
    fun `Given Arabic and Hebrew When mapped to Kord Then they remain selectable and round-trip safely`() {
        // Then
        assertEquals(Locale("ar"), DISCORD_LOCALE_MAP["ar"])
        assertEquals(Locale("he"), DISCORD_LOCALE_MAP["he"])
        assertEquals(LocaleCode("ar"), DISCORD_LOCALE_MAP.getValue("ar").toLocaleCode())
        assertEquals(LocaleCode("he"), DISCORD_LOCALE_MAP.getValue("he").toLocaleCode())
    }

    @Test
    fun `Given every catalog locale When converted to Kord and back Then preserve the canonical tag`() {
        // When / Then
        DiscordLocaleCatalog.supportedLocales.forEach { code ->
            val kordLocale = code.toKordLocaleOrNull()
            assertNotNull(kordLocale, "Missing Kord mapping for ${code.value}")
            val mapped = requireNotNull(kordLocale)
            assertEquals(code, mapped.toLocaleCode(), code.value)
            assertEquals(code, DISCORD_LOCALE_MAP.getValue(code.value).toLocaleCode(), code.value)
        }
    }

    @Test
    fun `Given a format-valid but non-catalog tag When converted Then reject it`() {
        // Given: Kord can parse this format, but Discord never exposes it as a locale
        val foreign = Locale("xx", "YY")
        val foreignCode = LocaleCode("xx-YY")

        // Then
        assertNull(foreign.toLocaleCodeOrNull())
        assertNull(foreignCode.toKordLocaleOrNull())
        assertThrows<IllegalArgumentException> { foreign.toLocaleCode() }

        // And a non-canonical case is rejected as well
        assertNull(LocaleCode("es-es").toKordLocaleOrNull())
        assertNull(Locale("es", "es").toLocaleCodeOrNull())
    }

    @Test
    fun `Given a locale with a country variant When converted Then require the canonical tag`() {
        // Then
        assertNull(Locale("ar", "SA").toLocaleCodeOrNull())
        assertEquals(LocaleCode("en-US"), Locale("en", "US").toLocaleCode())
        assertEquals(LocaleCode("es-419"), Locale("es", "419").toLocaleCode())
    }
}
