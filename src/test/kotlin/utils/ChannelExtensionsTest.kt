package utils

import dev.kord.common.Locale
import dev.kord.core.behavior.channel.BaseVoiceChannelBehavior
import es.wokis.utils.getLocale
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ChannelExtensionsTest {

    @Test
    fun `Given voice channel When getLocale is called Then return guild preferred locale`() = runTest {
        // Given
        val expected = Locale.DANISH
        val voiceChannel: BaseVoiceChannelBehavior = mockk {
            every { guild } returns mockk {
                coEvery { asGuildOrNull() } returns mockk {
                    every { preferredLocale } returns expected
                }
            }
        }
        // When
        val actual = voiceChannel.getLocale()

        // Then
        assertEquals(expected, actual)
    }

    @Test
    fun `Given voice channel with null guild When getLocale is called Then return US English`() = runTest {
        // Given
        val expected = Locale.ENGLISH_UNITED_STATES
        val voiceChannel: BaseVoiceChannelBehavior = mockk {
            every { guild } returns mockk {
                coEvery { asGuildOrNull() } returns null
            }
        }
        // When
        val actual = voiceChannel.getLocale()

        // Then
        assertEquals(expected, actual)
    }
}
