package domain.locale

import es.wokis.domain.locale.GetGuildLocaleUseCase
import es.wokis.domain.locale.LocaleCode
import es.wokis.domain.locale.LocaleRepository
import es.wokis.domain.model.GuildId
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class GetGuildLocaleUseCaseTest {

    private val localeRepository: LocaleRepository = mockk()
    private val getGuildLocaleUseCase = GetGuildLocaleUseCase(localeRepository)

    @Test
    fun `Given guild has custom locale When invoke is called Then return custom locale`() = runTest {
        // Given
        val guildId = GuildId(123uL)
        val expectedLocale = LocaleCode("es-ES")
        coEvery { localeRepository.getGuildLocale(guildId) } returns expectedLocale

        // When
        val result = getGuildLocaleUseCase(guildId)

        // Then
        assertEquals(expectedLocale, result)
        coVerify(exactly = 1) { localeRepository.getGuildLocale(guildId) }
    }

    @Test
    fun `Given guild has no custom locale When invoke is called Then return null`() = runTest {
        // Given
        val guildId = GuildId(123uL)
        coEvery { localeRepository.getGuildLocale(guildId) } returns null

        // When
        val result = getGuildLocaleUseCase(guildId)

        // Then
        assertNull(result)
        coVerify(exactly = 1) { localeRepository.getGuildLocale(guildId) }
    }

    @Test
    fun `Given max ULong guild id When invoke is called Then forward exact ids`() = runTest {
        // Given
        val guildId = GuildId(ULong.MAX_VALUE)
        val expectedLocale = LocaleCode("de")
        coEvery { localeRepository.getGuildLocale(guildId) } returns expectedLocale

        // When
        val result = getGuildLocaleUseCase(guildId)

        // Then
        assertEquals(expectedLocale, result)
        coVerify(exactly = 1) { localeRepository.getGuildLocale(GuildId(ULong.MAX_VALUE)) }
    }
}
