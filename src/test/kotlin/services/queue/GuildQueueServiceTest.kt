package services.queue

import dev.kord.common.Locale
import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.channel.BaseVoiceChannelBehavior
import dev.kord.core.entity.channel.MessageChannel
import dev.kord.core.entity.interaction.ApplicationCommandInteraction
import dev.kord.core.supplier.EntitySupplyStrategy
import es.wokis.exceptions.BotException
import es.wokis.player.engine.PlayerEngine
import es.wokis.services.lavaplayer.AudioPlayerManagerProvider
import es.wokis.services.lavaplayer.GuildLavaPlayerService
import es.wokis.services.localization.LocalizationService
import es.wokis.services.queue.GuildQueueService
import io.mockk.coEvery
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import mock.TestDispatchers
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
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
        val playerEngine: PlayerEngine = mockPlayerEngine()

        every { audioPlayerManagerProvider.createPlayerEngine() } returns playerEngine

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
    fun `Given dispatcher When getOrCreateLavaPlayerService is called for two guilds Then return GuildLavaPlayer for each Guild`() {
        // Given
        val guildId1 = Snowflake(123)
        val guildId2 = Snowflake(456)
        val textChannel: MessageChannel = mockk()
        val voiceChannel: BaseVoiceChannelBehavior = mockk()
        val playerEngine: PlayerEngine = mockPlayerEngine()

        every { audioPlayerManagerProvider.createPlayerEngine() } returns playerEngine

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
    fun `Given dispatcher When getOrCreateLavaPlayerService is called twice for the same guild Then return the same GuildLavaPlayer`() {
        // Given
        val guildId1 = Snowflake(123)
        val guildId2 = Snowflake(123)
        val textChannel: MessageChannel = mockk()
        val voiceChannel: BaseVoiceChannelBehavior = mockk()
        val playerEngine: PlayerEngine = mockPlayerEngine()

        every { audioPlayerManagerProvider.createPlayerEngine() } returns playerEngine

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
        val playerEngine: PlayerEngine = mockPlayerEngine()

        every { audioPlayerManagerProvider.createPlayerEngine() } returns playerEngine

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
        val playerEngine: PlayerEngine = mockPlayerEngine()

        every { audioPlayerManagerProvider.createPlayerEngine() } returns playerEngine
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
        val playerEngine: PlayerEngine = mockPlayerEngine()
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
        every { audioPlayerManagerProvider.createPlayerEngine() } returns playerEngine

        // When
        val actual = guildQueueService.getOrCreateLavaPlayerService(interaction)

        // Then
        assertTrue(actual is GuildLavaPlayerService)
    }

    @Test
    fun `Given interaction without voice channel When getOrCreateLavaPlayerService is called Then throw exception`() = runTest {
        // Given
        val mockGuildId = Snowflake(123)
        val textChannel: MessageChannel = mockk()
        val voiceChannel: BaseVoiceChannelBehavior = mockk()
        val playerEngine: PlayerEngine = mockPlayerEngine()
        val interaction: ApplicationCommandInteraction = mockk {
            every { guildLocale } returns Locale.BULGARIAN
            every { kord } returns mockk {
                every { resources } returns mockk {
                    every { defaultStrategy } returns EntitySupplyStrategy.rest
                }
                coEvery { getGuildOrNull(any()) } returns mockk {
                    coEvery { getMemberOrNull(any()) } returns mockk {
                        coEvery { getVoiceStateOrNull() } returns mockk {
                            coEvery { getChannelOrNull() } returns null // No voice channel
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
        every { audioPlayerManagerProvider.createPlayerEngine() } returns playerEngine
        coEvery { localizationService.getString(any(), any(), any()) } returns "No voice channel found"

        // When/Then
        assertThrows<BotException.UserException.NotInVoiceChannelException> {
            runBlocking { guildQueueService.getOrCreateLavaPlayerService(interaction) }
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
        coEvery { localizationService.getString(any(), any(), any()) } returns "No text channel found"

        // When/Then
        assertThrows<BotException.UserException.NotInTextChannelException> {
            runBlocking { guildQueueService.getOrCreateLavaPlayerService(interaction) }
        }
    }

    @Test
    fun `Given interaction without guild When getOrCreateLavaPlayerService is called Then throw NotInVoiceChannelException`() = runTest {
        // Given - when guildId is null, getMemberVoiceChannel returns null first
        val mockGuildId = null
        val textChannel: MessageChannel = mockk {
            coEvery { asChannelOrNull() } returns mockk() // Valid text channel
        }
        val interaction: ApplicationCommandInteraction = mockk {
            every { guildLocale } returns Locale.BULGARIAN
            every { kord } returns mockk()
            every { channel } returns textChannel
            every { data } returns mockk {
                every { guildId.value } returns mockGuildId // No guild
            }
        }
        coEvery { localizationService.getString(any(), any(), any()) } returns "No voice channel found"

        // When/Then - When guild is null, getMemberVoiceChannel returns null, so NotInVoiceChannelException is thrown
        assertThrows<BotException.UserException.NotInVoiceChannelException> {
            runBlocking { guildQueueService.getOrCreateLavaPlayerService(interaction) }
        }
    }

    @Test
    fun `Given concurrent first access for the same guild When creation runs Then the factory is invoked once and no engine is leaked`() {
        val guildId = Snowflake(123)
        val firstEngine = mockPlayerEngine()
        val losingEngine = mockPlayerEngine()
        every { audioPlayerManagerProvider.createPlayerEngine() } returns firstEngine andThen losingEngine
        val gate = CountDownLatch(1)
        val results = CopyOnWriteArrayList<GuildLavaPlayerService>()
        val threads = (0 until 8).map { index ->
            Thread({
                gate.await()
                results += guildQueueService.getOrCreateLavaPlayerService(
                    guildId = guildId,
                    textChannel = mockk(),
                    voiceChannel = mockk(),
                )
            }, "queue-first-access-$index")
        }

        threads.forEach { it.start() }
        gate.countDown()
        threads.forEach { it.join(5_000) }

        assertTrue(threads.none { it.isAlive })
        assertEquals(1, results.distinct().size)
        verify(exactly = 1) { audioPlayerManagerProvider.createPlayerEngine() }
        verify(exactly = 0) { losingEngine.close() }
    }

    @Test
    fun `Given created guild engines When shutdown runs Then every engine and the provider close once`() = runTest {
        val firstGuildId = Snowflake(123)
        val secondGuildId = Snowflake(456)
        val firstEngine = mockPlayerEngine()
        val secondEngine = mockPlayerEngine()
        every { audioPlayerManagerProvider.createPlayerEngine() } returns firstEngine andThen secondEngine
        justRun { audioPlayerManagerProvider.close() }
        guildQueueService.getOrCreateLavaPlayerService(
            guildId = firstGuildId,
            textChannel = mockk(),
            voiceChannel = mockk(),
        )
        guildQueueService.getOrCreateLavaPlayerService(
            guildId = secondGuildId,
            textChannel = mockk(),
            voiceChannel = mockk(),
        )

        guildQueueService.shutdown()
        guildQueueService.shutdown()

        verify(exactly = 1) { firstEngine.close() }
        verify(exactly = 1) { secondEngine.close() }
        verify(exactly = 1) { audioPlayerManagerProvider.close() }
        assertThrows<IllegalStateException> {
            guildQueueService.getOrCreateLavaPlayerService(
                guildId = firstGuildId,
                textChannel = mockk(),
                voiceChannel = mockk(),
            )
        }
    }

    private fun mockPlayerEngine(): PlayerEngine = mockk {
        every { events } returns emptyFlow()
        justRun { close() }
    }
}
