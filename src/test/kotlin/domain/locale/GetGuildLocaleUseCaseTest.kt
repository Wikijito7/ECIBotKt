package domain.locale

import dev.kord.common.Locale
import dev.kord.common.entity.Snowflake
import es.wokis.domain.locale.GetGuildLocaleUseCase
import es.wokis.repositories.locale.LocalJsonLocaleRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class GetGuildLocaleUseCaseTest {

    private val localeRepository: LocalJsonLocaleRepository = mockk()
    private val getGuildLocaleUseCase = GetGuildLocaleUseCase(localeRepository)

    @Test
    fun `Given guild has custom locale When invoke is called Then return custom locale`() = runTest {
        // Given
        val guildId = Snowflake(123)
        val expectedLocale = Locale.SPANISH_SPAIN
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
        val guildId = Snowflake(123)
        coEvery { localeRepository.getGuildLocale(guildId) } returns null

        // When
        val result = getGuildLocaleUseCase(guildId)

        // Then
        assertNull(result)
        coVerify(exactly = 1) { localeRepository.getGuildLocale(guildId) }
    }
}
