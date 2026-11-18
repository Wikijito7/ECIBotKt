package es.wokis.domain.locale

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DiscordLocaleCatalogTest {

    /**
     * The exact 34 canonical tags exposed by JDA 6.7.0 `DiscordLocale.locale`, excluding its
     * synthetic `unknown` marker. Hard-coding the list here makes any accidental addition,
     * removal or spelling change fail loudly instead of silently changing what can be persisted.
     */
    private val expectedTags = setOf(
        "ar", "bg", "zh-CN", "zh-TW", "hr", "cs", "da", "nl", "en-GB", "en-US", "fi", "fr", "de",
        "el", "he", "hi", "hu", "id", "it", "ja", "ko", "lt", "no", "pl", "pt-BR", "ro", "ru",
        "es-ES", "es-419", "sv-SE", "th", "tr", "uk", "vi",
    )

    @Test
    fun `Given the catalog When inspected Then expose exactly the real JDA canonical tags`() {
        val actualTags = DiscordLocaleCatalog.supportedLocales.map { it.value }

        assertEquals(34, actualTags.size)
        assertEquals(expectedTags, actualTags.toSet())
        assertEquals(actualTags.size, actualTags.toSet().size, "Canonical tags must be unique")
    }

    @Test
    fun `Given the real JDA tags When read Then Arabic and Hebrew are selectable`() {
        assertEquals(LocaleCode("ar"), DiscordLocaleCatalog.find("ar"))
        assertEquals(LocaleCode("he"), DiscordLocaleCatalog.find("he"))
        assertTrue(DiscordLocaleCatalog.isSupported(LocaleCode("ar")))
        assertTrue(DiscordLocaleCatalog.isSupported(LocaleCode("he")))
    }

    @Test
    fun `Given the catalog When inspected Then never expose unknown aliases or enum names`() {
        listOf(
            "unknown",
            "UNKNOWN",
            "Unknown",
            "reset",
            "RESET",
            "SPANISH",
            "ENGLISH_US",
            "Español",
            "es",
            "en",
            "xx-YY",
            "",
            " es-ES",
            "es-ES ",
        ).forEach { value ->
            assertNull(DiscordLocaleCatalog.find(value), "$value must not be a selectable catalog tag")
            assertFalse(DiscordLocaleCatalog.isSupported(LocaleCode(value)), "$value must not be supported")
        }
    }

    @Test
    fun `Given a canonical tag When looked up Then matching requires the exact canonical case`() {
        assertEquals(LocaleCode("es-ES"), DiscordLocaleCatalog.find("es-ES"))
        assertEquals(LocaleCode("en-US"), DiscordLocaleCatalog.find("en-US"))
        assertNull(DiscordLocaleCatalog.find("es-es"))
        assertNull(DiscordLocaleCatalog.find("ES-ES"))
        assertNull(DiscordLocaleCatalog.find("EN-US"))
        assertFalse(DiscordLocaleCatalog.isSupported(LocaleCode("ES-ES")))
    }

    @Test
    fun `Given a null or foreign locale When checked Then it is not supported`() {
        assertFalse(DiscordLocaleCatalog.isSupported(null))
        assertFalse(DiscordLocaleCatalog.isSupported(LocaleCode("xx-YY")))
        assertNull(DiscordLocaleCatalog.find(""))
    }

    @Test
    fun `Given the reset marker When matched Then it is case insensitive`() {
        assertTrue(DiscordLocaleCatalog.isReset("reset"))
        assertTrue(DiscordLocaleCatalog.isReset("RESET"))
        assertTrue(DiscordLocaleCatalog.isReset("ReSeT"))
        assertFalse(DiscordLocaleCatalog.isReset(null))
        assertFalse(DiscordLocaleCatalog.isReset(""))
        assertFalse(DiscordLocaleCatalog.isReset(" reset"))
        assertFalse(DiscordLocaleCatalog.isReset("reset "))
        assertEquals("reset", DiscordLocaleCatalog.RESET_INPUT)
        assertEquals("reset", RESET_LOCALE_VALUE)
    }

    @Test
    fun `Given no input When suggestions are requested Then return every canonical tag`() {
        val suggestions = DiscordLocaleCatalog.suggestions(null)

        assertEquals(DiscordLocaleCatalog.supportedLocales.take(25), suggestions)
    }

    @Test
    fun `Given an input When suggestions are requested Then match case-insensitively but return canonical spelling`() {
        val englishSuggestions = DiscordLocaleCatalog.suggestions("EN")
        val spanishSuggestions = DiscordLocaleCatalog.suggestions("es")

        assertEquals(listOf(LocaleCode("en-GB"), LocaleCode("en-US")), englishSuggestions)
        assertEquals(listOf(LocaleCode("es-ES"), LocaleCode("es-419")), spanishSuggestions)
        assertEquals(englishSuggestions, DiscordLocaleCatalog.suggestions("en"))
    }

    @Test
    fun `Given a query When suggestions are requested Then never exceed the requested limit`() {
        val suggestions = DiscordLocaleCatalog.suggestions("", limit = 2)

        assertEquals(listOf(LocaleCode("ar"), LocaleCode("bg")), suggestions)
    }

    @Test
    fun `Given an unmatched query When suggestions are requested Then return no suggestion`() {
        assertTrue(DiscordLocaleCatalog.suggestions("not-a-locale").isEmpty())
        assertTrue(DiscordLocaleCatalog.suggestions("unknown").isEmpty())
    }

    @Test
    fun `Given the fallback locale When inspected Then it is US English`() {
        assertEquals(LocaleCode("en-US"), DiscordLocaleCatalog.defaultLocale)
        assertTrue(DiscordLocaleCatalog.isSupported(DiscordLocaleCatalog.defaultLocale))
    }
}
