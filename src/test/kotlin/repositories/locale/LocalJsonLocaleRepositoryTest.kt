package repositories.locale

import dev.kord.common.Locale
import dev.kord.common.entity.Snowflake
import es.wokis.repositories.locale.LocalJsonLocaleRepository
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

class LocalJsonLocaleRepositoryTest {

    private lateinit var repository: LocalJsonLocaleRepository
    private val dataPath = "./data/"
    private val fileName = "guild_locales.json"
    private val dataFile = File(dataPath, fileName)

    @BeforeEach
    fun setup() {
        // Clean up any existing data file to ensure isolated tests
        dataFile.delete()
        
        // Create repository - it will create the file automatically
        repository = LocalJsonLocaleRepository()
    }

    @AfterEach
    fun tearDown() {
        // Clean up data file after each test
        dataFile.delete()
    }

    @Test
    fun `Given no custom locale When getGuildLocale is called Then return null`() = runTest {
        // Given
        val guildId = Snowflake(123)

        // When
        val result = repository.getGuildLocale(guildId)

        // Then
        assertNull(result)
    }

    @Test
    fun `Given custom locale set When getGuildLocale is called Then return locale`() = runTest {
        // Given
        val guildId = Snowflake(123)
        val locale = Locale.SPANISH_SPAIN

        // When
        repository.setGuildLocale(guildId, locale)
        val result = repository.getGuildLocale(guildId)

        // Then
        assertEquals(locale, result)
    }

    @Test
    fun `Given custom locale set When removeGuildLocale is called Then return null`() = runTest {
        // Given
        val guildId = Snowflake(123)
        val locale = Locale.SPANISH_SPAIN
        repository.setGuildLocale(guildId, locale)

        // When
        repository.removeGuildLocale(guildId)
        val result = repository.getGuildLocale(guildId)

        // Then
        assertNull(result)
    }

    @Test
    fun `Given multiple guilds When getAllGuildLocales is called Then return all mappings`() = runTest {
        // Given
        val guildId1 = Snowflake(123)
        val guildId2 = Snowflake(456)
        val locale1 = Locale.SPANISH_SPAIN
        val locale2 = Locale.FRENCH

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
        val guildId = Snowflake(123)
        val locale = Locale.SPANISH_SPAIN
        repository.setGuildLocale(guildId, locale)

        // When
        val newRepository = LocalJsonLocaleRepository()
        val result = newRepository.getGuildLocale(guildId)

        // Then
        assertEquals(locale, result)
    }
}
