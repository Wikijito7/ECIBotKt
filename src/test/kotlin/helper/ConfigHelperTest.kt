package helper

import es.wokis.helper.ConfigHelper
import es.wokis.helper.discordToken
import es.wokis.helper.isDebugMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

class ConfigHelperTest {

    private val configHelper = ConfigHelper()

    @Test
    fun `Given fresh config helper When getAllConfig is called Then return a map with default values`() {
        // When
        val actual = configHelper.config

        // Then
        assertFalse(actual.debug)
        assertTrue(actual.key.isNotEmpty())
    }

    @Test
    fun `Given config helper When isDebugMode is called Then return false`() {
        // When
        val actual = configHelper.isDebugMode

        // Then
        assertFalse(actual)
        assertEquals(configHelper.config.debug, configHelper.isDebugMode)
    }

    @Test
    fun `Given config helper When discordToken is called Then return false`() {
        // When
        val actual = configHelper.discordToken

        // Then
        assertTrue(actual.isNotEmpty())
        assertEquals(configHelper.config.key, configHelper.discordToken)
    }
}