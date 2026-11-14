package es.wokis.domain.locale

import es.wokis.domain.model.GuildId
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SetGuildLocaleUseCaseTest {

    private val guildId = GuildId(123uL)

    @Test
    fun `Given guild and locale When invoke is called Then save the exact locale`() = runTest {
        // Given
        val repository = FakeLocaleRepository()
        val useCase = SetGuildLocaleUseCase(repository)

        // When
        useCase(guildId, LocaleCode("es-ES"))

        // Then
        assertEquals(listOf(Operation.Set(guildId, LocaleCode("es-ES"))), repository.operations)
    }

    @Test
    fun `Given guild When removeLocale is called Then remove the guild locale`() = runTest {
        // Given
        val repository = FakeLocaleRepository()
        val useCase = SetGuildLocaleUseCase(repository)

        // When
        useCase.removeLocale(guildId)

        // Then
        assertEquals(listOf(Operation.Remove(guildId)), repository.operations)
    }

    @Test
    fun `Given max ULong guild id When invoke is called Then forward exact values`() = runTest {
        // Given
        val maxGuildId = GuildId(ULong.MAX_VALUE)
        val repository = FakeLocaleRepository()
        val useCase = SetGuildLocaleUseCase(repository)

        // When
        useCase(maxGuildId, LocaleCode("de"))

        // Then
        assertEquals(listOf(Operation.Set(maxGuildId, LocaleCode("de"))), repository.operations)
    }

    @Test
    fun `Given a valid catalog locale When invoke is called Then pass the canonical tag unchanged`() = runTest {
        // Given
        val repository = FakeLocaleRepository()
        val useCase = SetGuildLocaleUseCase(repository)

        // When
        useCase(guildId, LocaleCode("ar"))

        // Then the use case must not normalize, alias or translate the tag
        assertEquals(listOf(Operation.Set(guildId, LocaleCode("ar"))), repository.operations)
    }

    private sealed interface Operation {
        data class Set(val guildId: GuildId, val locale: LocaleCode) : Operation
        data class Remove(val guildId: GuildId) : Operation
    }

    private class FakeLocaleRepository : LocaleRepository {
        val operations = mutableListOf<Operation>()

        override suspend fun getGuildLocale(guildId: GuildId): LocaleCode? =
            error("not used by SetGuildLocaleUseCase")

        override suspend fun setGuildLocale(guildId: GuildId, locale: LocaleCode) {
            operations += Operation.Set(guildId, locale)
        }

        override suspend fun removeGuildLocale(guildId: GuildId) {
            operations += Operation.Remove(guildId)
        }

        override suspend fun getAllGuildLocales(): Map<GuildId, LocaleCode> =
            error("not used by SetGuildLocaleUseCase")
    }
}
