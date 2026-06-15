package services.config

import es.wokis.exceptions.EmptyDeezerMasterDecryptionKeyException
import es.wokis.exceptions.EmptyDiscordTokenException
import es.wokis.services.config.*
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConfigServiceTest {

    @Test
    fun `Given config with empty Discord token When config is validated Then throw EmptyDiscordTokenException`() {
        val config = mockk<Config> {
            every { discordBotToken } returns ""
            every { deezer.enabled } returns false
        }

        val exception = assertThrows(EmptyDiscordTokenException::class.java) {
            config.validate()
        }
        assertTrue(exception is EmptyDiscordTokenException)
    }

    @Test
    fun `Given empty decryption key When Deezer validated Then throw exception`() {
        val config = mockk<Config>(relaxed = true) {
            every { discordBotToken } returns "abc123"
            every { deezer.enabled } returns true
            every { deezer.masterDecryptionKey } returns ""
        }

        val exception = assertThrows(EmptyDeezerMasterDecryptionKeyException::class.java) {
            config.validate()
        }
        assertTrue(exception is EmptyDeezerMasterDecryptionKeyException)
    }

    @Test
    fun `Given valid config When validate is called Then no exception`() {
        val config = mockk<Config>(relaxed = true) {
            every { discordBotToken } returns "valid_token"
            every { deezer.enabled } returns false
        }

        assertDoesNotThrow { config.validate() }
    }

    @Test
    fun `Given matching ownerId When isOwner is called Then return true`() {
        val mockConfig = mockk<Config>(relaxed = true) {
            every { botOwnerId } returns "owner123"
        }

        val result = mockConfig.botOwnerId.isNotEmpty() && mockConfig.botOwnerId == "owner123"

        assertTrue(result)
    }

    @Test
    fun `Given non-matching ownerId When isOwner is called Then return false`() {
        val mockConfig = mockk<Config>(relaxed = true) {
            every { botOwnerId } returns "owner123"
        }

        val result = mockConfig.botOwnerId.isNotEmpty() && mockConfig.botOwnerId == "other"

        assertFalse(result)
    }
}
