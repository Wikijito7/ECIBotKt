package es.wokis.repositories.locale

import es.wokis.domain.locale.LocaleCode
import es.wokis.domain.model.GuildId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.IOException
import kotlin.test.assertFailsWith

class LocalJsonLocaleRepositoryTest {

    /**
     * Isolated data directory managed by JUnit. Every repository created by this test class
     * is backed by a file inside this directory, never by the production `./data` directory.
     */
    @TempDir
    lateinit var tempDir: File

    private val json = LocaleJson.json

    private val dataFile: File
        get() = File(tempDir, "guild_locales.json")

    @Test
    fun `Given no custom locale When getGuildLocale is called Then return null`() = runTest {
        val repository = newRepository()
        val guildId = GuildId(123uL)

        val result = repository.getGuildLocale(guildId)

        assertNull(result)
        assertFalse(dataFile.exists(), "A read must not create the backing file")
    }

    @Test
    fun `Given custom locale set When getGuildLocale is called Then return locale`() = runTest {
        val repository = newRepository()
        val guildId = GuildId(123uL)
        val locale = LocaleCode("es-ES")

        repository.setGuildLocale(guildId, locale)
        val result = repository.getGuildLocale(guildId)

        assertEquals(locale, result)
        assertTrue(dataFile.isFile)
    }

    @Test
    fun `Given custom locale set When removeGuildLocale is called Then return null`() = runTest {
        val repository = newRepository()
        val guildId = GuildId(123uL)
        val locale = LocaleCode("es-ES")
        repository.setGuildLocale(guildId, locale)

        repository.removeGuildLocale(guildId)
        val result = repository.getGuildLocale(guildId)

        assertNull(result)
        assertTrue(dataFile.isFile)
    }

    @Test
    fun `Given multiple guilds When getAllGuildLocales is called Then return all mappings`() = runTest {
        val repository = newRepository()
        val guildId1 = GuildId(123uL)
        val guildId2 = GuildId(456uL)
        val locale1 = LocaleCode("es-ES")
        val locale2 = LocaleCode("fr")

        repository.setGuildLocale(guildId1, locale1)
        repository.setGuildLocale(guildId2, locale2)

        val result = repository.getAllGuildLocales()

        assertEquals(2, result.size)
        assertEquals(locale1, result[guildId1])
        assertEquals(locale2, result[guildId2])
    }

    @Test
    fun `Given locale set When repository recreated Then locale persists`() = runTest {
        val repository = newRepository()
        val guildId = GuildId(123uL)
        val locale = LocaleCode("es-ES")
        repository.setGuildLocale(guildId, locale)

        val recreatedRepository = newRepository()
        val result = recreatedRepository.getGuildLocale(guildId)

        assertEquals(locale, result)
    }

    @Test
    fun `Given legacy JSON fixture with decimal keys When repository loads it Then locales are read`() = runTest {
        writeRawFile(
            """
            {
              "guild_locales": {
                "123": "es-ES",
                "456": "fr",
                "18446744073709551615": "de"
              }
            }
            """.trimIndent()
        )

        val repository = newRepository()

        assertEquals(LocaleCode("es-ES"), repository.getGuildLocale(GuildId(123uL)))
        assertEquals(LocaleCode("fr"), repository.getGuildLocale(GuildId(456uL)))
        assertEquals(LocaleCode("de"), repository.getGuildLocale(GuildId(ULong.MAX_VALUE)))
        assertEquals(3, repository.getAllGuildLocales().size)
    }

    @Test
    fun `Given locales set When written to file Then preserve legacy JSON shape and exact strings`() = runTest {
        val repository = newRepository()
        val guildId = GuildId(123uL)
        val maxGuildId = GuildId(ULong.MAX_VALUE)
        val locale = LocaleCode("es-ES")
        val maxLocale = LocaleCode("fr")

        repository.setGuildLocale(guildId, locale)
        repository.setGuildLocale(maxGuildId, maxLocale)

        val root = json.parseToJsonElement(dataFile.readText()).jsonObject
        assertEquals(setOf("guild_locales"), root.keys)

        val persistedLocales = root.getValue("guild_locales").jsonObject
        assertEquals(JsonPrimitive("es-ES"), persistedLocales[guildId.toString()])
        assertEquals(JsonPrimitive("fr"), persistedLocales[maxGuildId.toString()])
        assertTrue(persistedLocales.keys.all { it.toULongOrNull() != null })
        assertTrue(persistedLocales.values.all { it is JsonPrimitive && it.isString })

        val rawContent = dataFile.readText()
        assertTrue(rawContent.contains("\"guild_locales\""))
        assertFalse(rawContent.contains("guildLocales"))
    }

    @Test
    fun `Given ULong edge guild ids When file is reloaded Then keys round-trip exactly`() = runTest {
        val repository = newRepository()
        val expected = mapOf(
            GuildId(0uL) to LocaleCode("es-ES"),
            GuildId(1uL) to LocaleCode("fr"),
            GuildId(ULong.MAX_VALUE) to LocaleCode("de"),
            GuildId(ULong.MAX_VALUE - 1uL) to LocaleCode("it"),
            GuildId(Long.MAX_VALUE.toULong() + 1uL) to LocaleCode("en-US")
        )
        expected.forEach { (guildId, locale) ->
            repository.setGuildLocale(guildId, locale)
        }

        val recreatedRepository = newRepository()

        assertEquals(expected, recreatedRepository.getAllGuildLocales())
        expected.forEach { (guildId, locale) ->
            assertEquals(locale, recreatedRepository.getGuildLocale(guildId))
        }

        val persistedLocales = json.parseToJsonElement(dataFile.readText()).jsonObject
            .getValue("guild_locales")
            .jsonObject
        assertEquals(expected.keys.map { it.toString() }.toSet(), persistedLocales.keys)
    }

    @Test
    fun `Given invalid persisted locale When read Then exact string is returned`() = runTest {
        writeRawFile("""{"guild_locales":{"123":"not-a-locale"}}""")

        val repository = newRepository()

        assertEquals(LocaleCode("not-a-locale"), repository.getGuildLocale(GuildId(123uL)))
    }

    @Test
    fun `Given a raw invalid persisted value When all mappings are read Then preserve it unchanged`() = runTest {
        writeRawFile("""{"guild_locales":{"123":"not-a-locale"}}""")

        val repository = newRepository()

        // Then the persistence layer never sanitizes or replaces stored values
        assertEquals(
            mapOf(GuildId(123uL) to LocaleCode("not-a-locale")),
            repository.getAllGuildLocales(),
        )
    }

    @Test
    fun `Given a raw invalid locale When set Then persist the exact string and reload it`() = runTest {
        val repository = newRepository()
        val guildId = GuildId(123uL)

        repository.setGuildLocale(guildId, LocaleCode("invalid-locale"))

        val persistedLocales = json.parseToJsonElement(dataFile.readText()).jsonObject
            .getValue("guild_locales")
            .jsonObject
        assertEquals(JsonPrimitive("invalid-locale"), persistedLocales["123"])
        assertEquals(
            LocaleCode("invalid-locale"),
            newRepository().getGuildLocale(guildId),
        )
    }

    @Test
    fun `Given an existing locale When a new locale is set Then replace it in memory and on disk`() = runTest {
        val repository = newRepository()
        val guildId = GuildId(123uL)
        repository.setGuildLocale(guildId, LocaleCode("es-ES"))

        repository.setGuildLocale(guildId, LocaleCode("he"))

        assertEquals(LocaleCode("he"), repository.getGuildLocale(guildId))
        assertEquals(LocaleCode("he"), newRepository().getGuildLocale(guildId))

        val persistedLocales = json.parseToJsonElement(dataFile.readText()).jsonObject
            .getValue("guild_locales")
            .jsonObject
        assertEquals(JsonPrimitive("he"), persistedLocales["123"])
    }

    @Test
    fun `Given several guilds When one locale is removed Then only that guild is removed`() = runTest {
        val repository = newRepository()
        val removedGuild = GuildId(123uL)
        val keptGuild = GuildId(456uL)
        repository.setGuildLocale(removedGuild, LocaleCode("es-ES"))
        repository.setGuildLocale(keptGuild, LocaleCode("fr"))

        repository.removeGuildLocale(removedGuild)

        assertNull(repository.getGuildLocale(removedGuild))
        assertEquals(LocaleCode("fr"), repository.getGuildLocale(keptGuild))

        val persistedLocales = json.parseToJsonElement(dataFile.readText()).jsonObject
            .getValue("guild_locales")
            .jsonObject
        assertEquals(setOf("456"), persistedLocales.keys)

        val reloaded = newRepository()
        assertNull(reloaded.getGuildLocale(removedGuild))
        assertEquals(LocaleCode("fr"), reloaded.getGuildLocale(keptGuild))
    }

    @Test
    fun `Given empty file When repository is created Then start with empty map`() = runTest {
        writeRawFile("")

        val repository = newRepository()

        assertTrue(repository.getAllGuildLocales().isEmpty())
        assertNull(repository.getGuildLocale(GuildId(123uL)))
    }

    @Test
    fun `Given missing file When repository is created Then start with empty map without creating a file`() = runTest {
        assertFalse(dataFile.exists())

        val repository = newRepository()

        assertTrue(repository.getAllGuildLocales().isEmpty())
        assertFalse(dataFile.exists(), "Loading an absent file must not create it")
    }

    @Test
    fun `Given corrupt file When repository is created Then start with empty map and overwrite on write`() = runTest {
        writeRawFile("{ this is not valid json")

        val repository = newRepository()

        assertTrue(repository.getAllGuildLocales().isEmpty())

        repository.setGuildLocale(GuildId(123uL), LocaleCode("es-ES"))

        assertEquals(LocaleCode("es-ES"), repository.getGuildLocale(GuildId(123uL)))
        val persistedLocales = json.parseToJsonElement(dataFile.readText()).jsonObject
            .getValue("guild_locales")
            .jsonObject
        assertEquals(JsonPrimitive("es-ES"), persistedLocales["123"])
    }

    @Test
    fun `Given a legacy file with unknown fields When loaded Then known locales are still read`() = runTest {
        writeRawFile(
            """
            {
              "guild_locales": {
                "123": "es-ES"
              },
              "unexpected": true
            }
            """.trimIndent()
        )

        val repository = newRepository()

        assertEquals(LocaleCode("es-ES"), repository.getGuildLocale(GuildId(123uL)))
    }

    @Test
    fun `Given file cannot be written When setGuildLocale is called Then rethrow error`() = runTest {
        // Given: a directory occupies the backing-file path, so every write fails
        assertTrue(dataFile.mkdirs())
        val repository = newRepository()

        // When / Then
        assertFailsWith<IOException> {
            repository.setGuildLocale(GuildId(123uL), LocaleCode("es-ES"))
        }
    }

    @Test
    fun `Given file cannot be written When removeGuildLocale is called Then rethrow error`() = runTest {
        // Given: a directory occupies the backing-file path, so every write fails
        assertTrue(dataFile.mkdirs())
        val repository = newRepository()

        // When / Then
        assertFailsWith<IOException> {
            repository.removeGuildLocale(GuildId(123uL))
        }
    }

    @Test
    fun `Given concurrent writes When they complete Then every locale is kept`() = runTest {
        val repository = newRepository()
        val guildIds = (1..20).map { GuildId(it.toULong()) }
        val locale = LocaleCode("es-ES")

        coroutineScope {
            guildIds.map { guildId ->
                async(Dispatchers.Default) {
                    repository.setGuildLocale(guildId, locale)
                }
            }.awaitAll()
        }

        val result = repository.getAllGuildLocales()
        assertEquals(guildIds.toSet(), result.keys)
        assertTrue(result.values.all { it == locale })

        // And the persisted file also contains every mapping
        val reloaded = newRepository()
        assertEquals(guildIds.toSet(), reloaded.getAllGuildLocales().keys)
    }

    @Test
    fun `Given concurrent writes and reads When they complete Then no mapping is lost or corrupted`() = runTest {
        val repository = newRepository()
        val guildIds = (1..10).map { GuildId(it.toULong()) }
        val locale = LocaleCode("fr")

        coroutineScope {
            val writers = guildIds.map { guildId ->
                async(Dispatchers.Default) { repository.setGuildLocale(guildId, locale) }
            }
            val readers = (1..10).map {
                async(Dispatchers.Default) { repository.getAllGuildLocales() }
            }
            (writers + readers).awaitAll()
        }

        assertEquals(guildIds.toSet(), repository.getAllGuildLocales().keys)
        assertTrue(repository.getAllGuildLocales().values.all { it == locale })
    }

    /**
     * Builds a repository backed by [dataFile] inside the JUnit-managed [tempDir].
     */
    private fun newRepository(): LocalJsonLocaleRepository = newRepository(dataFile)

    /**
     * Builds a repository backed by [file], refusing any path outside [tempDir] so a test can
     * never read or write the real `./data` directory, even when an assertion later fails.
     */
    private fun newRepository(file: File): LocalJsonLocaleRepository {
        val tempRoot = tempDir.toPath().toAbsolutePath().normalize()
        val target = file.toPath().toAbsolutePath().normalize()
        check(target.startsWith(tempRoot)) {
            "Refusing to use $target: test data files must live under $tempRoot"
        }
        return LocalJsonLocaleRepository(json, file)
    }

    private fun writeRawFile(content: String) {
        dataFile.parentFile.mkdirs()
        dataFile.writeText(content)
    }
}
