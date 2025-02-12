package services.config

import es.wokis.services.config.ConfigService
import es.wokis.services.config.discordToken
import es.wokis.services.config.isDebugMode
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

class ConfigServiceTest {

    private val configService = mockk<ConfigService>()

    @Test
    fun `Given fresh config helper When getAllConfig is called Then return a map with default values`() {
        // Given
        every { configService.config } returns mockk {
            every { debug } returns false
            every { key } returns "manolete"
        }

        // When
        val actual = configService.config

        // Then
        assertFalse(actual.debug)
        assertTrue(actual.key.isNotEmpty())
    }

    @Test
    fun `Given config helper When isDebugMode is called Then return false`() {
        // Given
        every { configService.config } returns mockk {
            every { debug } returns false
        }

        // When
        val actual = configService.isDebugMode

        // Then
        assertFalse(actual)
        assertEquals(configService.config.debug, configService.isDebugMode)
    }

    @Test
    fun `Given config helper When discordToken is called Then return false`() {
        // Given
        every { configService.config } returns mockk {
            every { key } returns "pepe"
        }

        // When
        val actual = configService.discordToken

        // Then
        assertTrue(actual.isNotEmpty())
        assertEquals(configService.config.key, configService.discordToken)
    }
}
