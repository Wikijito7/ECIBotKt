package services.queue

import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventListener
import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.channel.BaseVoiceChannelBehavior
import dev.kord.core.entity.channel.MessageChannel
import es.wokis.services.lavaplayer.AudioPlayerManagerProvider
import es.wokis.services.lavaplayer.GuildLavaPlayerService
import es.wokis.services.queue.GuildQueueDispatcher
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import mock.TestDispatchers
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class GuildQueueDispatcherTest {

    private val audioPlayerManagerProvider: AudioPlayerManagerProvider = mockk()
    private val appDispatchers = TestDispatchers()

    private val guildQueueDispatcher = GuildQueueDispatcher(
        audioPlayerManagerProvider = audioPlayerManagerProvider,
        appDispatchers = appDispatchers
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
        val actual = guildQueueDispatcher.getOrCreateLavaPlayerService(
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
        val lavaPlayerGuild1 = guildQueueDispatcher.getOrCreateLavaPlayerService(
            guildId = guildId1,
            textChannel = textChannel,
            voiceChannel = voiceChannel
        )

        val lavaPlayerGuild2 = guildQueueDispatcher.getOrCreateLavaPlayerService(
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
        val lavaPlayerFirst = guildQueueDispatcher.getOrCreateLavaPlayerService(
            guildId = guildId1,
            textChannel = textChannel,
            voiceChannel = voiceChannel
        )

        val lavaPlayerSecond = guildQueueDispatcher.getOrCreateLavaPlayerService(
            guildId = guildId2,
            textChannel = textChannel,
            voiceChannel = voiceChannel
        )

        // Then
        assertEquals(lavaPlayerFirst, lavaPlayerSecond)
        assertSame(lavaPlayerFirst, lavaPlayerSecond)
    }
}
