package es.wokis.repositories.locale

import es.wokis.domain.locale.GetGuildLocaleUseCase
import es.wokis.domain.locale.LocaleCode
import es.wokis.domain.model.GuildId
import es.wokis.localization.GuildLocalizationService
import es.wokis.localization.LocalizationCatalog
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * Restart parity across the neutral seams the two runtimes share: the persisted guild locale is
 * written by one repository instance and read back by a recreated one, and the rehydrated value
 * still wins over the guild default and over the `en-US` fallback.
 *
 * The JDA bootstrap crossing (token/gateway wiring) stays out of scope; this pins the persistence
 * and resolution layers that the bootstrap merely composes.
 */
class LocaleRestartPersistenceTest {

    @TempDir
    lateinit var tempDir: File

    private val guildId = GuildId(123uL)
    private val otherGuildId = GuildId(456uL)
    private val key = "locale_command_description"

    @Test
    fun `Given a persisted Spanish locale When the repository is recreated Then resolution returns Spanish`() = runTest {
        repository().setGuildLocale(guildId, LocaleCode("es-ES"))

        val service = serviceOverRecreatedRepository()

        assertEquals(SPANISH_VALUE, service.getString(key, guildId, guildDefaultLocale = LocaleCode("fr")))
    }

    @Test
    fun `Given a persisted locale for another guild and a guild default When resolving Then the guild default wins`() =
        runTest {
            // Guild A stores `fr`: supported, but with no translation file, so a cross-guild leak
            // would render the English value and fail the Spanish assertion below.
            repository().setGuildLocale(guildId, LocaleCode("fr"))

            val service = serviceOverRecreatedRepository()

            assertEquals(
                SPANISH_VALUE,
                service.getString(key, otherGuildId, guildDefaultLocale = LocaleCode("es-ES")),
            )
        }

    @Test
    fun `Given a persisted locale for another guild When resolution runs Then fall back to US English`() = runTest {
        repository().setGuildLocale(guildId, LocaleCode("es-ES"))

        val service = serviceOverRecreatedRepository()

        val english = service.getString(key, otherGuildId)
        assertEquals(ENGLISH_VALUE, english)
        assertNotEquals(SPANISH_VALUE, english)
    }

    /** A fresh instance over the same file: the closest neutral stand-in for a process restart. */
    private fun serviceOverRecreatedRepository(): GuildLocalizationService = GuildLocalizationService(
        getGuildLocaleUseCase = GetGuildLocaleUseCase(repository()),
        catalog = LocalizationCatalog.load(),
    )

    private fun repository(): LocalJsonLocaleRepository =
        LocalJsonLocaleRepository(LocaleJson.json, File(tempDir, "guild_locales.json"))

    private companion object {
        // Copied from `locale_command_description` in core/src/main/resources/lang/lang.yml / lang_es-ES.yml
        private const val ENGLISH_VALUE = "Set the preferred locale for this guild"
        private const val SPANISH_VALUE = "Establecer el idioma preferido para este servidor"
    }
}
