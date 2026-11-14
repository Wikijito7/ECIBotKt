package es.wokis.domain.locale

import es.wokis.domain.model.GuildId
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class GetGuildLocaleUseCaseTest {

    private val guildId = GuildId(123uL)

    @Test
    fun `Given guild has custom locale When invoke is called Then return custom locale`() = runTest {
        // Given
        val repository = FakeLocaleRepository(mapOf(guildId to LocaleCode("es-ES")))
        val useCase = GetGuildLocaleUseCase(repository)

        // When
        val result = useCase(guildId)

        // Then
        assertEquals(LocaleCode("es-ES"), result)
        assertEquals(listOf(guildId), repository.reads)
    }

    @Test
    fun `Given guild has no custom locale When invoke is called Then return null`() = runTest {
        // Given
        val repository = FakeLocaleRepository()
        val useCase = GetGuildLocaleUseCase(repository)

        // When
        val result = useCase(guildId)

        // Then
        assertNull(result)
        assertEquals(listOf(guildId), repository.reads)
    }

    @Test
    fun `Given max ULong guild id When invoke is called Then forward the exact id`() = runTest {
        // Given
        val maxGuildId = GuildId(ULong.MAX_VALUE)
        val repository = FakeLocaleRepository(mapOf(maxGuildId to LocaleCode("de")))
        val useCase = GetGuildLocaleUseCase(repository)

        // When
        val result = useCase(maxGuildId)

        // Then
        assertEquals(LocaleCode("de"), result)
        assertEquals(listOf(maxGuildId), repository.reads)
    }

    @Test
    fun `Given a raw malformed stored value When invoke is called Then return it unchanged`() = runTest {
        // Given
        val repository = FakeLocaleRepository(mapOf(guildId to LocaleCode("not-a-locale")))
        val useCase = GetGuildLocaleUseCase(repository)

        // When
        val result = useCase(guildId)

        // Then the use case is a read-through: validation belongs to the localization layer
        assertEquals(LocaleCode("not-a-locale"), result)
    }

    private class FakeLocaleRepository(
        private val locales: Map<GuildId, LocaleCode> = emptyMap(),
    ) : LocaleRepository {
        val reads = mutableListOf<GuildId>()

        override suspend fun getGuildLocale(guildId: GuildId): LocaleCode? {
            reads += guildId
            return locales[guildId]
        }

        override suspend fun setGuildLocale(guildId: GuildId, locale: LocaleCode) =
            error("not used by GetGuildLocaleUseCase")

        override suspend fun removeGuildLocale(guildId: GuildId) =
            error("not used by GetGuildLocaleUseCase")

        override suspend fun getAllGuildLocales(): Map<GuildId, LocaleCode> = locales
    }
}
