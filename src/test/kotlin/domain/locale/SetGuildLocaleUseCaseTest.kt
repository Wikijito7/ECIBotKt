package domain.locale

import es.wokis.domain.locale.LocaleCode
import es.wokis.domain.locale.LocaleRepository
import es.wokis.domain.locale.SetGuildLocaleUseCase
import es.wokis.domain.model.GuildId
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class SetGuildLocaleUseCaseTest {

    private val localeRepository: LocaleRepository = mockk()
    private val setGuildLocaleUseCase = SetGuildLocaleUseCase(localeRepository)

    @Test
    fun `Given guild and locale When invoke is called Then save locale`() = runTest {
        // Given
        val guildId = GuildId(123uL)
        val locale = LocaleCode("es-ES")
        coJustRun { localeRepository.setGuildLocale(guildId, locale) }

        // When
        setGuildLocaleUseCase(guildId, locale)

        // Then
        coVerify(exactly = 1) { localeRepository.setGuildLocale(guildId, locale) }
    }

    @Test
    fun `Given guild When removeLocale is called Then remove locale`() = runTest {
        // Given
        val guildId = GuildId(123uL)
        coJustRun { localeRepository.removeGuildLocale(guildId) }

        // When
        setGuildLocaleUseCase.removeLocale(guildId)

        // Then
        coVerify(exactly = 1) { localeRepository.removeGuildLocale(guildId) }
    }

    @Test
    fun `Given max ULong guild id When invoke is called Then forward exact values`() = runTest {
        // Given
        val guildId = GuildId(ULong.MAX_VALUE)
        val locale = LocaleCode("de")
        coJustRun { localeRepository.setGuildLocale(guildId, locale) }

        // When
        setGuildLocaleUseCase(guildId, locale)

        // Then
        coVerify(exactly = 1) {
            localeRepository.setGuildLocale(GuildId(ULong.MAX_VALUE), LocaleCode("de"))
        }
    }
}
