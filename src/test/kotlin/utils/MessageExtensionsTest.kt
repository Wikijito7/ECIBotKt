package utils

import dev.kord.common.Locale
import dev.kord.common.entity.Snowflake
import dev.kord.core.entity.Message
import dev.kord.core.supplier.EntitySupplyStrategy
import es.wokis.utils.getGuildLocale
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class MessageExtensionsTest {

    @Test
    fun `Given message When getGuildLocale is called Then return guild locale`() = runTest {
        // Given
        val expected = Locale.BULGARIAN
        val message: Message = mockk {
            every { data.guildId.value } returns Snowflake(123)
            every { kord } returns mockk {
                every { resources.defaultStrategy } returns EntitySupplyStrategy.rest
                coEvery { getGuildOrNull(any()) } returns mockk {
                    every { preferredLocale } returns expected
                }
            }
        }

        // When
        val actual = message.getGuildLocale()

        //Then
        assertEquals(expected, actual)
    }

    @Test
    fun `Given message without locale When getGuildLocale is called Then return guild locale`() = runTest {
        // Given
        val expected = Locale.ENGLISH_UNITED_STATES
        val message: Message = mockk {
            every { data.guildId.value } returns null
        }

        // When
        val actual = message.getGuildLocale()

        //Then
        assertEquals(expected, actual)
    }

    @Test
    fun `Given message with invalid guild When getGuildLocale is called Then return guild locale`() = runTest {
        // Given
        val expected = Locale.ENGLISH_UNITED_STATES
        val message: Message = mockk {
            every { data.guildId.value } returns Snowflake(123)
            every { kord } returns mockk {
                every { resources.defaultStrategy } returns EntitySupplyStrategy.rest
                coEvery { getGuildOrNull(any()) } returns null
            }
        }

        // When
        val actual = message.getGuildLocale()

        //Then
        assertEquals(expected, actual)
    }
}
