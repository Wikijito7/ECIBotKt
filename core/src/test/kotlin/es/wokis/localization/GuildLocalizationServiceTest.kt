package es.wokis.localization

import es.wokis.domain.locale.DiscordLocaleCatalog
import es.wokis.domain.locale.GetGuildLocaleUseCase
import es.wokis.domain.locale.LocaleCode
import es.wokis.domain.locale.LocaleRepository
import es.wokis.domain.model.GuildId
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class GuildLocalizationServiceTest {

    private val guildId = GuildId(123uL)

    private val catalog = LocalizationCatalog.of(
        mapOf(
            "translated" to mapOf(
                LocaleCode("en-US") to "English value",
                LocaleCode("es-ES") to "Valor en español",
            ),
            "only_english" to mapOf(
                LocaleCode("en-US") to "Only English %s",
            ),
            "hello" to mapOf(
                LocaleCode("en-US") to "Hello %s",
                LocaleCode("es-ES") to "Hola %s",
            ),
        ),
    )

    @Test
    fun `Given a persisted supported locale When resolving Then use that locale`() = runTest {
        // Given
        val service = service(persisted = LocaleCode("es-ES"))

        // Then
        assertEquals("Valor en español", service.getString("translated", guildId))
    }

    @Test
    fun `Given a persisted supported locale and guild default When resolving Then persisted wins`() = runTest {
        // Given
        val service = service(persisted = LocaleCode("es-ES"))

        // Then
        assertEquals(
            "Valor en español",
            service.getString("translated", guildId, guildDefaultLocale = LocaleCode("fr")),
        )
    }

    @Test
    fun `Given no persisted locale and a guild default When resolving Then use the guild default`() = runTest {
        // Given
        val service = service(persisted = null)

        // Then
        assertEquals(
            "Valor en español",
            service.getString("translated", guildId, guildDefaultLocale = LocaleCode("es-ES")),
        )
    }

    @Test
    fun `Given a malformed persisted value and a guild default When resolving Then fall back to the guild default`() =
        runTest {
            // Given
            val service = service(persisted = LocaleCode("not-a-locale"))

            // Then
            assertEquals(
                "Valor en español",
                service.getString("translated", guildId, guildDefaultLocale = LocaleCode("es-ES")),
            )
        }

    @Test
    fun `Given a malformed persisted value and no guild default When resolving Then fall back to US English`() =
        runTest {
            // Given
            val service = service(persisted = LocaleCode("not-a-locale"))

            // Then
            assertEquals("English value", service.getString("translated", guildId))
        }

    @Test
    fun `Given a non-community guild with no guild default When resolving Then use US English`() = runTest {
        // Given: the adapter supplies no guild default, as Discord does for non-Community guilds
        val service = service(persisted = null)

        // Then
        assertEquals("English value", service.getString("translated", guildId))
        assertEquals(DiscordLocaleCatalog.defaultLocale, LocaleCode("en-US"))
    }

    @Test
    fun `Given a supported locale without translations When resolving Then fall back per key to English`() =
        runTest {
            listOf("ar", "he").forEach { tag ->
                // Given: Arabic and Hebrew are selectable but have no fabricated translation file
                val service = service(persisted = LocaleCode(tag))

                // Then the valid locale is selected, and only the missing key falls back
                assertEquals(
                    "English value",
                    service.getString("translated", guildId, guildDefaultLocale = LocaleCode("es-ES")),
                    "$tag must use the English fallback instead of the guild default",
                )
            }
        }

    @Test
    fun `Given an untranslated key When resolving Then fall back per key to English`() = runTest {
        // Given
        val service = service(persisted = LocaleCode("es-ES"))

        // Then
        assertEquals("Only English %s", service.getString("only_english", guildId))
    }

    @Test
    fun `Given no guild and no default locale When resolving Then use US English`() = runTest {
        // Given
        val service = service(persisted = LocaleCode("es-ES"))

        // Then
        assertEquals("English value", service.getString("translated", guildId = null))
    }

    @Test
    fun `Given a guild default outside the catalog When resolving Then ignore it and use US English`() = runTest {
        // Given
        val service = service(persisted = null)

        // Then
        assertEquals(
            "English value",
            service.getString("translated", guildId, guildDefaultLocale = LocaleCode("xx-YY")),
        )
    }

    @Test
    fun `Given a format key When formatting Then resolve the locale before formatting`() = runTest {
        // Given
        val service = service(persisted = LocaleCode("es-ES"))

        // When
        val result = service.getStringFormat("hello", guildId, arguments = arrayOf("mundo"))

        // Then
        assertEquals("Hola mundo", result)
    }

    @Test
    fun `Given a key When the default is requested Then return the English value`() {
        // Given
        val service = service(persisted = null)

        // Then
        assertEquals("English value", service.getDefaultString("translated"))
    }

    @Test
    fun `Given a key When all localizations are requested Then forward the catalog map`() {
        // Given
        val service = service(persisted = null)

        // Then
        assertEquals(
            mapOf(LocaleCode("en-US") to "English value", LocaleCode("es-ES") to "Valor en español"),
            service.getLocalizations("translated"),
        )
    }

    @Test
    fun `Given an unknown key When resolving Then fail loudly`() = runTest {
        // Given
        val service = service(persisted = null)

        // Then
        assertThrows<NoLocalizationFoundException> {
            service.getString("totally_unknown_key", guildId)
        }
    }

    private fun service(persisted: LocaleCode?): GuildLocalizationService =
        GuildLocalizationService(
            getGuildLocaleUseCase = GetGuildLocaleUseCase(FixedLocaleRepository(persisted)),
            catalog = catalog,
        )

    private class FixedLocaleRepository(
        private val persisted: LocaleCode?,
    ) : LocaleRepository {
        override suspend fun getGuildLocale(guildId: GuildId): LocaleCode? = persisted

        override suspend fun setGuildLocale(guildId: GuildId, locale: LocaleCode) =
            error("not used by GuildLocalizationService")

        override suspend fun removeGuildLocale(guildId: GuildId) =
            error("not used by GuildLocalizationService")

        override suspend fun getAllGuildLocales(): Map<GuildId, LocaleCode> = emptyMap()
    }
}
