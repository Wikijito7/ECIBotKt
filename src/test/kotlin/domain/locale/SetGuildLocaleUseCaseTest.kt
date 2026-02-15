package domain.locale

import dev.kord.common.Locale
import dev.kord.common.entity.Snowflake
import es.wokis.domain.locale.SetGuildLocaleUseCase
import es.wokis.repositories.locale.LocalJsonLocaleRepository
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class SetGuildLocaleUseCaseTest {

    private val localeRepository: LocalJsonLocaleRepository = mockk(relaxed = true)
    private val setGuildLocaleUseCase = SetGuildLocaleUseCase(localeRepository)

    @Test
    fun `Given guild and locale When invoke is called Then save locale`() = runTest {
        // Given
        val guildId = Snowflake(123)
        val locale = Locale.SPANISH_SPAIN

        // When
        setGuildLocaleUseCase(guildId, locale)

        // Then
        coVerify(exactly = 1) { localeRepository.setGuildLocale(guildId, locale) }
    }

    @Test
    fun `Given guild When removeLocale is called Then remove locale`() = runTest {
        // Given
        val guildId = Snowflake(123)

        // When
        setGuildLocaleUseCase.removeLocale(guildId)

        // Then
        coVerify(exactly = 1) { localeRepository.removeGuildLocale(guildId) }
    }
}
