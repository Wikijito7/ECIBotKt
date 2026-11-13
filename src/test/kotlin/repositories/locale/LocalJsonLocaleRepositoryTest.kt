package repositories.locale

import es.wokis.domain.locale.LocaleCode
import es.wokis.domain.model.GuildId
import es.wokis.repositories.locale.LocalJsonLocaleRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.*
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

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    private val dataFile: File
        get() = File(tempDir, "guild_locales.json")

    @Test
    fun `Given no custom locale When getGuildLocale is called Then return null`() = runTest {
        // Given
        val repository = newRepository()
        val guildId = GuildId(123uL)

        // When
        val result = repository.getGuildLocale(guildId)

        // Then
        assertNull(result)
        assertFalse(dataFile.exists(), "A read must not create the backing file")
    }

    @Test
    fun `Given custom locale set When getGuildLocale is called Then return locale`() = runTest {
        // Given
        val repository = newRepository()
        val guildId = GuildId(123uL)
        val locale = LocaleCode("es-ES")

        // When
        repository.setGuildLocale(guildId, locale)
        val result = repository.getGuildLocale(guildId)

        // Then
        assertEquals(locale, result)
        assertTrue(dataFile.isFile)
    }

    @Test
    fun `Given custom locale set When removeGuildLocale is called Then return null`() = runTest {
        // Given
        val repository = newRepository()
        val guildId = GuildId(123uL)
        val locale = LocaleCode("es-ES")
        repository.setGuildLocale(guildId, locale)

        // When
        repository.removeGuildLocale(guildId)
        val result = repository.getGuildLocale(guildId)

        // Then
        assertNull(result)
        assertTrue(dataFile.isFile)
    }

    @Test
    fun `Given multiple guilds When getAllGuildLocales is called Then return all mappings`() = runTest {
        // Given
        val repository = newRepository()
        val guildId1 = GuildId(123uL)
        val guildId2 = GuildId(456uL)
        val locale1 = LocaleCode("es-ES")
        val locale2 = LocaleCode("fr")

        repository.setGuildLocale(guildId1, locale1)
        repository.setGuildLocale(guildId2, locale2)

        // When
        val result = repository.getAllGuildLocales()

        // Then
        assertEquals(2, result.size)
        assertEquals(locale1, result[guildId1])
        assertEquals(locale2, result[guildId2])
    }

    @Test
    fun `Given locale set When repository recreated Then locale persists`() = runTest {
        // Given
        val repository = newRepository()
        val guildId = GuildId(123uL)
        val locale = LocaleCode("es-ES")
        repository.setGuildLocale(guildId, locale)

        // When
        val recreatedRepository = newRepository()
        val result = recreatedRepository.getGuildLocale(guildId)

        // Then
        assertEquals(locale, result)
    }

    @Test
    fun `Given legacy JSON fixture with decimal keys When repository loads it Then locales are read`() = runTest {
        // Given
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

        // When
        val repository = newRepository()

        // Then
        assertEquals(LocaleCode("es-ES"), repository.getGuildLocale(GuildId(123uL)))
        assertEquals(LocaleCode("fr"), repository.getGuildLocale(GuildId(456uL)))
        assertEquals(LocaleCode("de"), repository.getGuildLocale(GuildId(ULong.MAX_VALUE)))
        assertEquals(3, repository.getAllGuildLocales().size)
    }

    @Test
    fun `Given locales set When written to file Then preserve legacy JSON shape and exact strings`() = runTest {
        // Given
        val repository = newRepository()
        val guildId = GuildId(123uL)
        val maxGuildId = GuildId(ULong.MAX_VALUE)
        val locale = LocaleCode("es-ES")
        val maxLocale = LocaleCode("fr")

        // When
        repository.setGuildLocale(guildId, locale)
        repository.setGuildLocale(maxGuildId, maxLocale)

        // Then
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
        // Given
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

        // When
        val recreatedRepository = newRepository()

        // Then
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
        // Given
        writeRawFile("""{"guild_locales":{"123":"not-a-locale"}}""")

        // When
        val repository = newRepository()

        // Then
        assertEquals(LocaleCode("not-a-locale"), repository.getGuildLocale(GuildId(123uL)))
    }

    @Test
    fun `Given empty file When repository is created Then start with empty map`() = runTest {
        // Given
        writeRawFile("")

        // When
        val repository = newRepository()

        // Then
        assertTrue(repository.getAllGuildLocales().isEmpty())
        assertNull(repository.getGuildLocale(GuildId(123uL)))
    }

    @Test
    fun `Given missing file When repository is created Then start with empty map without creating a file`() = runTest {
        // Given
        assertFalse(dataFile.exists())

        // When
        val repository = newRepository()

        // Then
        assertTrue(repository.getAllGuildLocales().isEmpty())
        assertFalse(dataFile.exists(), "Loading an absent file must not create it")
    }

    @Test
    fun `Given corrupt file When repository is created Then start with empty map and overwrite on write`() = runTest {
        // Given
        writeRawFile("{ this is not valid json")

        // When
        val repository = newRepository()

        // Then
        assertTrue(repository.getAllGuildLocales().isEmpty())

        repository.setGuildLocale(GuildId(123uL), LocaleCode("es-ES"))

        assertEquals(LocaleCode("es-ES"), repository.getGuildLocale(GuildId(123uL)))
        val persistedLocales = json.parseToJsonElement(dataFile.readText()).jsonObject
            .getValue("guild_locales")
            .jsonObject
        assertEquals(JsonPrimitive("es-ES"), persistedLocales["123"])
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
        // Given
        val repository = newRepository()
        val guildIds = (1..20).map { GuildId(it.toULong()) }
        val locale = LocaleCode("es-ES")

        // When
        coroutineScope {
            guildIds.map { guildId ->
                async(Dispatchers.Default) {
                    repository.setGuildLocale(guildId, locale)
                }
            }.awaitAll()
        }

        // Then
        val result = repository.getAllGuildLocales()
        assertEquals(guildIds.toSet(), result.keys)
        assertTrue(result.values.all { it == locale })

        // And the persisted file also contains every mapping
        val reloaded = newRepository()
        assertEquals(guildIds.toSet(), reloaded.getAllGuildLocales().keys)
    }

    @Test
    fun `Given concurrent writes and reads When they complete Then no mapping is lost or corrupted`() = runTest {
        // Given
        val repository = newRepository()
        val guildIds = (1..10).map { GuildId(it.toULong()) }
        val locale = LocaleCode("fr")

        // When
        coroutineScope {
            val writers = guildIds.map { guildId ->
                async(Dispatchers.Default) { repository.setGuildLocale(guildId, locale) }
            }
            val readers = (1..10).map {
                async(Dispatchers.Default) { repository.getAllGuildLocales() }
            }
            (writers + readers).awaitAll()
        }

        // Then
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
