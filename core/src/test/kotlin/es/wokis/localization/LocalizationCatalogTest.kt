package es.wokis.localization

import es.wokis.domain.locale.DiscordLocaleCatalog
import es.wokis.domain.locale.LocaleCode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class LocalizationCatalogTest {

    private val catalog = LocalizationCatalog.load()

    @Test
    fun `Given the moved resources When the catalog loads Then both languages are visible`() {
        val localizations = catalog.getLocalizations("locale_command_description")

        assertEquals(
            setOf(LocaleCode("en-US"), LocaleCode("es-ES")),
            localizations.keys,
        )
        assertTrue(localizations.getValue(LocaleCode("en-US")).isNotBlank())
        assertTrue(localizations.getValue(LocaleCode("es-ES")).isNotBlank())
    }

    @Test
    fun `Given the moved resource files When compared Then every key is parsed from its own language`() {
        val englishKeys = resourceKeys("lang.yml")
        val spanishKeys = resourceKeys("lang_es-ES.yml")

        // Then both moved files are non-empty and the catalog resolved every key
        assertTrue(englishKeys.isNotEmpty())
        assertTrue(spanishKeys.isNotEmpty())

        englishKeys.forEach { key ->
            assertTrue(
                catalog.getLocalizations(key).containsKey(LocaleCode("en-US")),
                "Missing English value for $key",
            )
        }
        spanishKeys.forEach { key ->
            assertTrue(
                catalog.getLocalizations(key).containsKey(LocaleCode("es-ES")),
                "Missing Spanish value for $key",
            )
        }

        // And no key ever exposes a locale outside the two files that were moved
        (englishKeys + spanishKeys).forEach { key ->
            assertTrue(
                catalog.getLocalizations(key).keys.all {
                    it == LocaleCode("en-US") || it == LocaleCode("es-ES")
                },
                "Unexpected locale for $key",
            )
        }
    }

    @Test
    fun `Given the neutral locale command keys When read Then they resolve from the shared classpath`() {
        listOf(
            LocaleMessageKeys.LOCALE_COMMAND_DESCRIPTION,
            LocaleMessageKeys.LOCALE_COMMAND_INPUT_DESCRIPTION,
            LocaleMessageKeys.LOCALE_COMMAND_SUCCESS,
            LocaleMessageKeys.LOCALE_COMMAND_RESET_SUCCESS,
            LocaleMessageKeys.LOCALE_COMMAND_INVALID_LOCALE,
            LocaleMessageKeys.ERROR_NO_GUILD,
            LocaleMessageKeys.ERROR_NO_CONTENT_PROVIDED,
        ).forEach { key ->
            assertTrue(catalog.getDefaultString(key).isNotBlank(), "Missing English value for $key")
            assertEquals(2, catalog.getLocalizations(key).size, "Missing translation for $key")
        }
    }

    @Test
    fun `Given a Spanish localization When requested Then return the Spanish value`() {
        assertEquals(
            "Establecer el idioma preferido para este servidor",
            catalog.getString("locale_command_description", LocaleCode("es-ES")),
        )
    }

    @Test
    fun `Given Arabic or Hebrew When requested Then fall back per key to English without fabricated translations`() {
        val english = catalog.getDefaultString("locale_command_description")

        listOf("ar", "he").forEach { tag ->
            assertTrue(DiscordLocaleCatalog.isSupported(LocaleCode(tag)))
            assertEquals(english, catalog.getString("locale_command_description", LocaleCode(tag)), tag)
            assertNull(
                LocalizationCatalog::class.java.getResource("/lang/lang_$tag.yml"),
                "$tag must not ship a fabricated translation file",
            )
        }
    }

    @Test
    fun `Given an explicit catalog When the key is missing in the requested locale Then fall back to English`() {
        val explicit = LocalizationCatalog.of(
            mapOf(
                "key" to mapOf(
                    LocaleCode("en-US") to "english",
                    LocaleCode("es-ES") to "spanish",
                ),
            ),
        )

        assertEquals("spanish", explicit.getString("key", LocaleCode("es-ES")))
        assertEquals("english", explicit.getString("key", LocaleCode("fr")))
        assertEquals("english", explicit.getString("key", LocaleCode("ar")))
        assertEquals("english", explicit.getString("key", null))
        assertEquals("english", explicit.getDefaultString("key"))
    }

    @Test
    fun `Given an explicit catalog When inspected Then only canonical locales are accepted`() {
        val explicit = LocalizationCatalog.of(
            mapOf("key" to mapOf(LocaleCode("en-US") to "english")),
        )

        assertEquals(setOf(LocaleCode("en-US")), explicit.getLocalizations("key").keys)
        assertFalse(explicit.getLocalizations("key").containsKey(LocaleCode("unknown")))
    }

    @Test
    fun `Given the shared resources When error_unexpected_with_debug is read Then preserve delimiter and placeholder`() {
        val key = "error_unexpected_with_debug"

        val english = catalog.getString(key, LocaleCode("en-US"))
        val spanish = catalog.getString(key, LocaleCode("es-ES"))

        // Then the value is not truncated at the first `: ` delimiter
        assertEquals("An unexpected error occurred: %s", english)
        assertEquals("Ocurrió un error inesperado: %s", spanish)
        assertTrue(english.contains(": "))
        assertTrue(english.endsWith("%s"))
        assertTrue(spanish.contains(": "))
        assertTrue(spanish.endsWith("%s"))
        assertFalse(english.endsWith("occurred"))
        assertFalse(spanish.endsWith("inesperado"))
    }

    @Test
    fun `Given an unknown key When requested Then fail loudly`() {
        assertThrows<NoLocalizationFoundException> {
            catalog.getLocalizations("totally_unknown_key")
        }
        assertThrows<NoLocalizationFoundException> {
            catalog.getString("totally_unknown_key", LocaleCode("en-US"))
        }
        assertThrows<NoLocalizationFoundException> {
            catalog.getDefaultString("totally_unknown_key")
        }
    }

    @Test
    fun `Given the shared resources When resolved Then the locale folder exists on the runtime classpath`() {
        assertNotNull(LocalizationCatalog::class.java.getResource("/lang"))
        assertNotNull(LocalizationCatalog::class.java.getResource("/lang/lang.yml"))
        assertNotNull(LocalizationCatalog::class.java.getResource("/lang/lang_es-ES.yml"))
    }

    private fun resourceKeys(fileName: String): Set<String> {
        val stream = requireNotNull(LocalizationCatalog::class.java.getResourceAsStream("/lang/$fileName")) {
            "Missing test resource /lang/$fileName"
        }
        return stream.bufferedReader().useLines { lines ->
            lines.filter { it.isNotBlank() }
                .map { it.substringBefore(": ") }
                .toSet()
        }
    }
}
