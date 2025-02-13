package services.config

import es.wokis.exceptions.DiscordKeyIsNullException
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

    @Test
    fun `Given config helper with null key When discordToken is called Then throw DiscordKeyIsNullException`() {
        // Given
        every { configService.config } returns mockk {
            every { key } returns ""
        }

        // When
        try {
            configService.discordToken
        } catch (e: Throwable) {
            // Then
            assertTrue(e is DiscordKeyIsNullException)
        }
    }

    @Test
    fun `Given config helper with youtube oauth2 token When youtubeOauth2Token is called Then return token`() {
        // Given
        every { configService.config } returns mockk {
            every { youtubeOauth2Token } returns "pepe"
        }

        // When
        val actual = configService.youtubeOauth2Token

        // Then
        assertTrue(actual?.isNotEmpty() == true)
        assertEquals(configService.config.youtubeOauth2Token, configService.youtubeOauth2Token)
    }

    @Test
    fun `Given config helper with default youtube oauth2 token When youtubeOauth2Token is called Then return token`() {
        // Given
        every { configService.config } returns mockk {
            every { youtubeOauth2Token } returns DEFAULT_YOUTUBE_OAUTH2_VALUE
        }

        // When
        val actual = configService.youtubeOauth2Token

        // Then
        assertNull(actual)
    }

    @Test
    fun `Given config helper with empty youtube oauth2 token When youtubeOauth2Token is called Then return token`() {
        // Given
        every { configService.config } returns mockk {
            every { youtubeOauth2Token } returns ""
        }

        // When
        val actual = configService.youtubeOauth2Token

        // Then
        assertNull(actual)
    }
}
