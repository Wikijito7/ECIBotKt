package services.config

import es.wokis.exceptions.EmptyDeezerMasterDecryptionKeyException
import es.wokis.exceptions.EmptyDiscordTokenException
import es.wokis.services.config.*
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

class ConfigServiceTest {

    private val configService = mockk<ConfigService>()

    @Test
    fun `Given fresh config helper When getAllConfig is called Then return a map with default values`() {
        // Given
        every { configService.config } returns mockk {
            every { debug } returns false
            every { discordBotToken } returns "manolete"
        }

        // When
        val actual = configService.config

        // Then
        assertFalse(actual.debug)
        assertTrue(actual.discordBotToken.isNotEmpty())
    }

    @Test
    fun `Given config with empty Discord token When config is validated Then throw EmptyDiscordTokenException`() {
        // Given
        val config = mockk<Config> {
            every { discordBotToken } returns ""
            every { deezer.enabled } returns false
        }

        // When
        try {
            config.validate()
        } catch (e: Throwable) {
            // Then
            assertTrue(e is EmptyDiscordTokenException)
        }
    }

    @Test
    fun `Given config with Deezer enabled but empty decryption key When config is validated Then throw EmptyDeezerMasterDecryptionKeyException`() {
        // Given
        val config = mockk<Config>(relaxed = true) {
            every { discordBotToken } returns "abc123"
            every { deezer.enabled } returns true
            every { deezer.masterDecryptionKey } returns ""
        }

        // When
        try {
            config.validate()
        } catch (e: Throwable) {
            // Then
            assertTrue(e is EmptyDeezerMasterDecryptionKeyException)
        }
    }
}
