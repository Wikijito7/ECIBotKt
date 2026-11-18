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

class PlayerLocalizationTest {

    private val guildId = GuildId(314uL)
    private val catalog = LocalizationCatalog.load()

    @Test
    fun `Given a persisted Spanish locale When a player key resolves Then Spanish wins over the guild default`() = runTest {
        // Given: a guild whose persisted locale is Spanish and whose Discord default is French.
        val service = service(persisted = LocaleCode("es-ES"))

        val message = service.getString(
            key = PlayerMessageKeys.PLAYER_PAUSED,
            guildId = guildId,
            guildDefaultLocale = LocaleCode("fr"),
        )

        // Then: only the persisted guild locale decides the player text.
        assertEquals("Reproducción pausada.", message)
    }

    @Test
    fun `Given no persisted locale and a Spanish guild default When a player key resolves Then Spanish is used`() = runTest {
        val service = service(persisted = null)

        val message = service.getString(
            key = PlayerMessageKeys.PLAYER_PAUSED,
            guildId = guildId,
            guildDefaultLocale = LocaleCode("es-ES"),
        )

        assertEquals("Reproducción pausada.", message)
    }

    @Test
    fun `Given neither a persisted nor a guild locale When a player key resolves Then US English is used`() = runTest {
        val service = service(persisted = null)

        val message = service.getString(
            key = PlayerMessageKeys.PLAYER_PAUSED,
            guildId = guildId,
            guildDefaultLocale = null,
        )

        assertEquals("Playback paused.", message)
        assertEquals(DiscordLocaleCatalog.defaultLocale, LocaleCode("en-US"))
    }

    @Test
    fun `Given a Spanish request for a player key without a translation When resolved Then only that key falls back`() =
        runTest {
            // Given: an explicit catalog where the player key only ships the English value.
            val partial = LocalizationCatalog.of(
                mapOf(
                    PlayerMessageKeys.PLAYER_STOPPED to mapOf(LocaleCode("en-US") to "Stopped"),
                    PlayerMessageKeys.PLAYER_PAUSED to mapOf(
                        LocaleCode("en-US") to "Playback paused.",
                        LocaleCode("es-ES") to "Reproducción pausada.",
                    ),
                ),
            )
            val service = GuildLocalizationService(
                getGuildLocaleUseCase = GetGuildLocaleUseCase(FixedLocaleRepository(LocaleCode("es-ES"))),
                catalog = partial,
            )

            // Then: the untranslated key falls back per key, and a translated sibling stays Spanish.
            assertEquals("Stopped", service.getString(PlayerMessageKeys.PLAYER_STOPPED, guildId))
            assertEquals("Reproducción pausada.", service.getString(PlayerMessageKeys.PLAYER_PAUSED, guildId))
        }

    @Test
    fun `Given the shipped catalog When every player key is read Then both language files provide it`() {
        val keys = declaredPlayerKeys()

        assertTrue(keys.size >= 70, "the neutral player surface must stay catalogued")
        keys.forEach { key ->
            val localizations = catalog.getLocalizations(key)
            assertEquals(
                setOf(LocaleCode("en-US"), LocaleCode("es-ES")),
                localizations.keys,
                "Missing translation for player key $key",
            )
            assertTrue(localizations.getValue(LocaleCode("en-US")).isNotBlank(), "Empty English value for $key")
            assertTrue(localizations.getValue(LocaleCode("es-ES")).isNotBlank(), "Empty Spanish value for $key")
        }
    }

    @Test
    fun `Given the player channel keys When read Then both catalogs ship every announcement`() {
        val channelKeys = listOf(
            PlayerMessageKeys.PLAYER_CHANNEL_CREATED to "Created <#%s> channel for music controls",
            PlayerMessageKeys.PLAYER_CHANNEL_CREATION_FAILED to
                "Could not create player channel. Showing player here instead.",
            PlayerMessageKeys.PLAYER_SHOWN_ON_EXISTING_CHANNEL to "Created player on <#%s>",
        )

        channelKeys.forEach { (key, english) ->
            val localizations = catalog.getLocalizations(key)
            assertEquals(english, localizations.getValue(LocaleCode("en-US")), "Missing English value for $key")
            assertEquals(setOf(LocaleCode("en-US"), LocaleCode("es-ES")), localizations.keys)
            assertTrue(localizations.getValue(LocaleCode("es-ES")).isNotBlank(), "Empty Spanish value for $key")
        }
    }

    @Test
    fun `Given the player command keys When read Then descriptions are non-blank and translated`() {
        listOf(
            PlayerMessageKeys.PLAY_COMMAND_DESCRIPTION,
            PlayerMessageKeys.PLAY_COMMAND_INPUT_DESCRIPTION,
            PlayerMessageKeys.PLAYER_COMMAND_DESCRIPTION,
            PlayerMessageKeys.PLAYER_COMMAND_ACTION_DESCRIPTION,
        ).forEach { key ->
            assertTrue(catalog.getDefaultString(key).isNotBlank(), "Missing English description for $key")
            assertEquals(2, catalog.getLocalizations(key).size, "Missing translation for $key")
        }
    }

    private fun service(persisted: LocaleCode?): GuildLocalizationService = GuildLocalizationService(
        getGuildLocaleUseCase = GetGuildLocaleUseCase(FixedLocaleRepository(persisted)),
        catalog = catalog,
    )

    /** Reads the public `const val` string keys declared by the neutral player key object. */
    private fun declaredPlayerKeys(): List<String> = PlayerMessageKeys::class.java.declaredFields
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
