package services.queue

import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventListener
import dev.kord.common.Locale
import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.channel.BaseVoiceChannelBehavior
import dev.kord.core.entity.channel.MessageChannel
import dev.kord.core.entity.interaction.ApplicationCommandInteraction
import dev.kord.core.supplier.EntitySupplyStrategy
import es.wokis.services.lavaplayer.AudioPlayerManagerProvider
import es.wokis.services.lavaplayer.GuildLavaPlayerService
import es.wokis.services.localization.LocalizationService
import es.wokis.services.queue.GuildQueueService
import io.mockk.coEvery
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import mock.TestDispatchers
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class GuildQueueServiceTest {

    private val audioPlayerManagerProvider: AudioPlayerManagerProvider = mockk()
    private val appDispatchers = TestDispatchers()
    private val localizationService: LocalizationService = mockk()

    private val guildQueueService = GuildQueueService(
        audioPlayerManagerProvider = audioPlayerManagerProvider,
        appDispatchers = appDispatchers,
        localizationService = localizationService
    )

    @Test
    fun `Given dispatcher When getOrCreateLavaPlayerService is called Then create and return GuildLavaPlayer for given Guild`() {
        // Given
        val guildId = Snowflake(123)
        val textChannel: MessageChannel = mockk()
        val voiceChannel: BaseVoiceChannelBehavior = mockk()
        val audioPlayerManager: AudioPlayerManager = mockk()

        every { audioPlayerManagerProvider.createAudioPlayerManager() } returns audioPlayerManager
        every { audioPlayerManager.createPlayer() } returns mockk {
            justRun { addListener(any<AudioEventListener>()) }
        }

        // When
        val actual = guildQueueService.getOrCreateLavaPlayerService(
            guildId = guildId,
            textChannel = textChannel,
            voiceChannel = voiceChannel
        )

        // Then
        assertTrue(actual is GuildLavaPlayerService)
    }

    @Test
    fun `Given dispatcher When getOrCreateLavaPlayerService is called for two different guilds Then create and return GuildLavaPlayer for each Guild`() {
        // Given
        val guildId1 = Snowflake(123)
        val guildId2 = Snowflake(456)
        val textChannel: MessageChannel = mockk()
        val voiceChannel: BaseVoiceChannelBehavior = mockk()
        val audioPlayerManager: AudioPlayerManager = mockk()

        every { audioPlayerManagerProvider.createAudioPlayerManager() } returns audioPlayerManager
        every { audioPlayerManager.createPlayer() } returns mockk {
            justRun { addListener(any<AudioEventListener>()) }
        }

        // When
        val lavaPlayerGuild1 = guildQueueService.getOrCreateLavaPlayerService(
            guildId = guildId1,
            textChannel = textChannel,
            voiceChannel = voiceChannel
        )

        val lavaPlayerGuild2 = guildQueueService.getOrCreateLavaPlayerService(
            guildId = guildId2,
            textChannel = textChannel,
            voiceChannel = voiceChannel
        )

        // Then
        assertFalse(lavaPlayerGuild1 == lavaPlayerGuild2)
    }

    @Test
    fun `Given dispatcher When getOrCreateLavaPlayerService is called for the same guild twice Then create and return the same GuildLavaPlayer`() {
        // Given
        val guildId1 = Snowflake(123)
        val guildId2 = Snowflake(123)
        val textChannel: MessageChannel = mockk()
        val voiceChannel: BaseVoiceChannelBehavior = mockk()
        val audioPlayerManager: AudioPlayerManager = mockk()

        every { audioPlayerManagerProvider.createAudioPlayerManager() } returns audioPlayerManager
        every { audioPlayerManager.createPlayer() } returns mockk {
            justRun { addListener(any<AudioEventListener>()) }
        }

        // When
        val lavaPlayerFirst = guildQueueService.getOrCreateLavaPlayerService(
            guildId = guildId1,
            textChannel = textChannel,
            voiceChannel = voiceChannel
        )

        val lavaPlayerSecond = guildQueueService.getOrCreateLavaPlayerService(
            guildId = guildId2,
            textChannel = textChannel,
            voiceChannel = voiceChannel
        )

        // Then
        assertEquals(lavaPlayerFirst, lavaPlayerSecond)
        assertSame(lavaPlayerFirst, lavaPlayerSecond)
    }

    @Test
    fun `Given dispatcher When getLavaPlayerService is called on an unknown guild Then return null`() {
        // Given
        val guildId = Snowflake(123)
        val audioPlayerManager: AudioPlayerManager = mockk()

        every { audioPlayerManagerProvider.createAudioPlayerManager() } returns audioPlayerManager
        every { audioPlayerManager.createPlayer() } returns mockk {
            justRun { addListener(any<AudioEventListener>()) }
        }

        // When
        val actual = guildQueueService.getLavaPlayerService(guildId = guildId)

        // Then
        assertNull(actual)
    }

    @Test
    fun `Given dispatcher When getLavaPlayerService is called Then return given guild audio player`() {
        // Given
        val guildId = Snowflake(123)
        val textChannel: MessageChannel = mockk()
        val voiceChannel: BaseVoiceChannelBehavior = mockk()
        val audioPlayerManager: AudioPlayerManager = mockk()

        every { audioPlayerManagerProvider.createAudioPlayerManager() } returns audioPlayerManager
        every { audioPlayerManager.createPlayer() } returns mockk {
            justRun { addListener(any<AudioEventListener>()) }
        }
        guildQueueService.getOrCreateLavaPlayerService(guildId, textChannel, voiceChannel)

        // When
        val actual = guildQueueService.getLavaPlayerService(guildId = guildId)

        // Then
        assertNotNull(actual)
    }

    @Test
    fun `Given interaction When getOrCreateLavaPlayerService is called Then GuildLavaPlayerService is returned`() = runTest {
        // Given
        val mockGuildId = Snowflake(123)
        val textChannel: MessageChannel = mockk {
            coEvery { asChannelOrNull() } returns mockk()
        }
        val voiceChannel: BaseVoiceChannelBehavior = mockk()
        val audioPlayerManager: AudioPlayerManager = mockk()
        val interaction: ApplicationCommandInteraction = mockk {
            every { guildLocale } returns Locale.BULGARIAN
            every { kord } returns mockk {
                every { resources } returns mockk {
                    every { defaultStrategy } returns EntitySupplyStrategy.rest
                }
                coEvery { getGuildOrNull(any()) } returns mockk {
                    coEvery { getMemberOrNull(any()) } returns mockk {
                        coEvery { getVoiceStateOrNull() } returns mockk {
                            coEvery { getChannelOrNull() } returns voiceChannel
                        }
                    }
                }
                every { user } returns mockk {
                    every { id } returns mockk()
                }
            }
            every { channel } returns textChannel
            every { data } returns mockk {
                every { guildId.value } returns mockGuildId
            }
        }
        every { audioPlayerManagerProvider.createAudioPlayerManager() } returns audioPlayerManager
        every { audioPlayerManager.createPlayer() } returns mockk {
            justRun { addListener(any<AudioEventListener>()) }
        }

        // When
        val actual = guildQueueService.getOrCreateLavaPlayerService(interaction)

        // Then
        assertTrue(actual is GuildLavaPlayerService)
    }

    @Test
    fun `Given interaction without voice channel When getOrCreateLavaPlayerService is called Then throw exception`() = runTest {
        // Given
        val mockGuildId = Snowflake(123)
        val textChannel: MessageChannel = mockk {
            coEvery { asChannelOrNull() } returns null
        }
        val voiceChannel: BaseVoiceChannelBehavior = mockk()
        val audioPlayerManager: AudioPlayerManager = mockk()
        val interaction: ApplicationCommandInteraction = mockk {
            every { guildLocale } returns Locale.BULGARIAN
            every { kord } returns mockk {
                every { resources } returns mockk {
                    every { defaultStrategy } returns EntitySupplyStrategy.rest
                }
                coEvery { getGuildOrNull(any()) } returns mockk {
                    coEvery { getMemberOrNull(any()) } returns mockk {
                        coEvery { getVoiceStateOrNull() } returns mockk {
                            coEvery { getChannelOrNull() } returns voiceChannel
                        }
                    }
                }
                every { user } returns mockk {
                    every { id } returns mockk()
                }
            }
            every { channel } returns textChannel
            every { data } returns mockk {
                every { guildId.value } returns mockGuildId
            }
        }
        every { audioPlayerManagerProvider.createAudioPlayerManager() } returns audioPlayerManager
        every { audioPlayerManager.createPlayer() } returns mockk {
            justRun { addListener(any<AudioEventListener>()) }
        }
        every { localizationService.getString(any(), any()) } returns "No text channel found"

        // When
        try {
            guildQueueService.getOrCreateLavaPlayerService(interaction)
        } catch (e: IllegalStateException) {
            // Then
            assertEquals("No text channel found", e.message)
        }
    }

    @Test
    fun `Given interaction without text channel When getOrCreateLavaPlayerService is called Then throw exception`() = runTest {
        // Given
        val mockGuildId = Snowflake(123)
        val textChannel: MessageChannel = mockk {
            coEvery { asChannelOrNull() } returns null
        }
        val voiceChannel: BaseVoiceChannelBehavior = mockk()
        val interaction: ApplicationCommandInteraction = mockk {
            every { guildLocale } returns Locale.BULGARIAN
            every { kord } returns mockk {
                every { resources } returns mockk {
                    every { defaultStrategy } returns EntitySupplyStrategy.rest
                }
                coEvery { getGuildOrNull(any()) } returns mockk {
                    coEvery { getMemberOrNull(any()) } returns mockk {
                        coEvery { getVoiceStateOrNull() } returns mockk {
                            coEvery { getChannelOrNull() } returns voiceChannel
                        }
                    }
                }
                every { user } returns mockk {
                    every { id } returns mockk()
                }
            }
            every { channel } returns textChannel
            every { data } returns mockk {
                every { guildId.value } returns mockGuildId
            }
        }
        every { localizationService.getString(any(), any()) } returns "No text channel found"

        // When
        try {
            guildQueueService.getOrCreateLavaPlayerService(interaction)
        } catch (e: IllegalStateException) {
            // Then
            assertEquals("No text channel found", e.message)
        }
    }

    @Test
    fun `Given interaction without guild When getOrCreateLavaPlayerService is called Then throw exception`() = runTest {
        // Given
        val mockGuildId = null
        val textChannel: MessageChannel = mockk {
            coEvery { asChannelOrNull() } returns null
        }
        val voiceChannel: BaseVoiceChannelBehavior = mockk()
        val interaction: ApplicationCommandInteraction = mockk {
            every { guildLocale } returns Locale.BULGARIAN
            every { kord } returns mockk {
                every { resources } returns mockk {
                    every { defaultStrategy } returns EntitySupplyStrategy.rest
                }
                coEvery { getGuildOrNull(any()) } returns mockk {
                    coEvery { getMemberOrNull(any()) } returns mockk {
                        coEvery { getVoiceStateOrNull() } returns mockk {
                            coEvery { getChannelOrNull() } returns voiceChannel
                        }
                    }
                }
                every { user } returns mockk {
                    every { id } returns mockk()
                }
            }
            every { channel } returns textChannel
            every { data } returns mockk {
                every { guildId.value } returns mockGuildId
            }
        }
        every { localizationService.getString(any(), any()) } returns "No guild id found"

        // When
        try {
            guildQueueService.getOrCreateLavaPlayerService(interaction)
        } catch (e: IllegalStateException) {
            // Then
            assertEquals("No guild id found", e.message)
        }
    }
}
