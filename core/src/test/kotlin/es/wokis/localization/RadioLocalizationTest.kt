package es.wokis.localization

import es.wokis.domain.locale.DiscordLocaleCatalog
import es.wokis.domain.locale.GetGuildLocaleUseCase
import es.wokis.domain.locale.LocaleCode
import es.wokis.domain.locale.LocaleRepository
import es.wokis.domain.model.GuildId
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.lang.reflect.Modifier

class RadioLocalizationTest {

    private val guildId = GuildId(314uL)
    private val catalog = LocalizationCatalog.load()

    @Test
    fun `Given a persisted Spanish locale When a radio key resolves Then Spanish wins over the guild default`() = runTest {
        val service = service(persisted = LocaleCode("es-ES"))

        val message = service.getString(
            key = RadioMessageKeys.RADIO_PLAY_FOUND,
            guildId = guildId,
            guildDefaultLocale = LocaleCode("fr"),
        )

        assertEquals("Emisora encontrada, sintonizando…", message)
    }

    @Test
    fun `Given neither a persisted nor a guild locale When a radio key resolves Then US English is used`() = runTest {
        val service = service(persisted = null)

        val message = service.getString(
            key = RadioMessageKeys.RADIO_UNKNOWN_MESSAGE,
            guildId = guildId,
            guildDefaultLocale = null,
        )

        assertEquals("Unknown message", message)
        assertEquals(DiscordLocaleCatalog.defaultLocale, LocaleCode("en-US"))
    }

    @Test
    fun `Given the shipped catalog When every radio key is read Then both language files provide it`() {
        val keys = declaredRadioKeys()

        assertEquals(22, keys.size, "the radio surface must keep exactly Kord's 22 keys")
        keys.forEach { key ->
            val localizations = catalog.getLocalizations(key)
            assertEquals(
                setOf(LocaleCode("en-US"), LocaleCode("es-ES")),
                localizations.keys,
                "Missing translation for radio key $key",
            )
            assertTrue(localizations.getValue(LocaleCode("en-US")).isNotBlank(), "Empty English value for $key")
            assertTrue(localizations.getValue(LocaleCode("es-ES")).isNotBlank(), "Empty Spanish value for $key")
        }
    }

    @Test
    fun `Given the radio descriptions When read Then English is non blank and Spanish is translated`() {
        listOf(
            RadioMessageKeys.RADIO_COMMAND_DESCRIPTION,
            RadioMessageKeys.RADIO_PLAY_COMMAND_DESCRIPTION,
            RadioMessageKeys.RADIO_PLAY_INPUT_DESCRIPTION,
            RadioMessageKeys.RADIO_LIST_COMMAND_DESCRIPTION,
            RadioMessageKeys.RADIO_SEARCH_COMMAND_DESCRIPTION,
            RadioMessageKeys.RADIO_SEARCH_NAME_COMMAND_DESCRIPTION,
            RadioMessageKeys.RADIO_SEARCH_COUNTRYCODE_COMMAND_DESCRIPTION,
            RadioMessageKeys.RADIO_SEARCH_NAME_INPUT_DESCRIPTION,
            RadioMessageKeys.RADIO_SEARCH_COUNTRYCODE_INPUT_DESCRIPTION,
            RadioMessageKeys.RADIO_RANDOM_COMMAND_DESCRIPTION,
            RadioMessageKeys.RADIO_COUNTRYCODES_COMMAND_DESCRIPTION,
        ).forEach { key ->
            assertTrue(catalog.getDefaultString(key).isNotBlank(), "Missing English description for $key")
            assertEquals(2, catalog.getLocalizations(key).size, "Missing translation for $key")
        }
    }

    private fun service(persisted: LocaleCode?): GuildLocalizationService = GuildLocalizationService(
        getGuildLocaleUseCase = GetGuildLocaleUseCase(FixedLocaleRepository(persisted)),
        catalog = catalog,
    )

    /** Reads the public `const val` string keys declared by the neutral radio key object. */
    private fun declaredRadioKeys(): List<String> = RadioMessageKeys::class.java.declaredFields
        .filter { field -> field.type == String::class.java && Modifier.isStatic(field.modifiers) }
        .map { field -> field.get(null) as String }
        .distinct()

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
