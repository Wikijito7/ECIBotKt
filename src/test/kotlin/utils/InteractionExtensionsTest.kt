package utils

import dev.kord.common.entity.Snowflake
import dev.kord.common.entity.optional.OptionalSnowflake
import dev.kord.core.Kord
import dev.kord.core.entity.interaction.ChatInputCommandInteraction
import dev.kord.core.supplier.EntitySupplyStrategy
import es.wokis.utils.getMemberVoiceChannel
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class InteractionExtensionsTest {

    @Test
    fun `Given interaction When getMemberVoiceChannel is called Then return the voice channel`() = runTest {
        // Given
        val interaction = mockk<ChatInputCommandInteraction> {
            every { data } returns mockk {
                every { guildId } returns OptionalSnowflake.Value(123u)
            }
            every { user } returns mockk {
                every { id } returns Snowflake(567u)
            }
        }
        val bot = mockk<Kord> {
            every { resources } returns mockk {
                every { defaultStrategy } returns EntitySupplyStrategy.rest
            }
            coEvery { getGuildOrNull(any(), any()) } returns mockk {
                coEvery { getMemberOrNull(any()) } returns mockk {
                    coEvery { getVoiceStateOrNull() } returns mockk {
                        coEvery { getChannelOrNull() } returns mockk()
                    }
                }
            }
        }

        // When
        val actual = interaction.getMemberVoiceChannel(bot)

        // Then
        assertNotNull(actual)
    }

    @Test
    fun `Given interaction without guildId When getMemberVoiceChannel is called Then return null`() = runTest {
        // Given
        val interaction = mockk<ChatInputCommandInteraction> {
            every { data } returns mockk {
                every { guildId } returns OptionalSnowflake.Missing
            }
        }
        val bot = mockk<Kord>()

        // When
        val actual = interaction.getMemberVoiceChannel(bot)

        // Then
        assertNull(actual)
    }

    @Test
    fun `Given interaction with invalid guild When getMemberVoiceChannel is called Then return null`() = runTest {
        // Given
        val interaction = mockk<ChatInputCommandInteraction> {
            every { data } returns mockk {
                every { guildId } returns OptionalSnowflake.Value(123u)
            }
        }
        val bot = mockk<Kord> {
            coEvery { resources } returns mockk {
                every { defaultStrategy } returns EntitySupplyStrategy.rest
            }
            coEvery { getGuildOrNull(any(), any()) } returns null
        }

        // When
        val actual = interaction.getMemberVoiceChannel(bot)

        // Then
        assertNull(actual)
    }

    @Test
    fun `Given interaction with null member When getMemberVoiceChannel is called Then return null`() = runTest {
        // Given
        val interaction = mockk<ChatInputCommandInteraction> {
            every { data } returns mockk {
                every { guildId } returns OptionalSnowflake.Value(123u)
            }
            every { user } returns mockk {
                every { id } returns Snowflake(567u)
            }
        }
        val bot = mockk<Kord> {
            every { resources } returns mockk {
                every { defaultStrategy } returns EntitySupplyStrategy.rest
            }
            coEvery { getGuildOrNull(any(), any()) } returns mockk {
                coEvery { getMemberOrNull(any()) } returns null
            }
        }

        // When
        val actual = interaction.getMemberVoiceChannel(bot)

        // Then
        assertNull(actual)
    }

    @Test
    fun `Given interaction with member without voice state When getMemberVoiceChannel is called Then return null`() = runTest {
        // Given
        val interaction = mockk<ChatInputCommandInteraction> {
            every { data } returns mockk {
                every { guildId } returns OptionalSnowflake.Value(123u)
            }
            every { user } returns mockk {
                every { id } returns Snowflake(567u)
            }
        }
        val bot = mockk<Kord> {
            every { resources } returns mockk {
                every { defaultStrategy } returns EntitySupplyStrategy.rest
            }
            coEvery { getGuildOrNull(any(), any()) } returns mockk {
                coEvery { getMemberOrNull(any()) } returns mockk {
                    coEvery { getVoiceStateOrNull() } returns null
                }
            }
        }

        // When
        val actual = interaction.getMemberVoiceChannel(bot)

        // Then
        assertNull(actual)
    }

    @Test
    fun `Given interaction with member with voice state without present voice channel When getMemberVoiceChannel is called Then return null`() = runTest {
        // Given
        val interaction = mockk<ChatInputCommandInteraction> {
            every { data } returns mockk {
                every { guildId } returns OptionalSnowflake.Value(123u)
            }
            every { user } returns mockk {
                every { id } returns Snowflake(567u)
            }
        }
        val bot = mockk<Kord> {
            every { resources } returns mockk {
                every { defaultStrategy } returns EntitySupplyStrategy.rest
            }
            coEvery { getGuildOrNull(any(), any()) } returns mockk {
                coEvery { getMemberOrNull(any()) } returns mockk {
                    coEvery { getVoiceStateOrNull() } returns mockk {
                        coEvery { getChannelOrNull() } returns null
                    }
                }
            }
        }

        // When
        val actual = interaction.getMemberVoiceChannel(bot)

        // Then
        assertNull(actual)
    }
}
