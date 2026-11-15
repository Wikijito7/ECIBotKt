package services.lavaplayer

import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.channel.BaseVoiceChannelBehavior
import dev.kord.core.entity.channel.MessageChannel
import es.wokis.player.engine.PlayerEngine
import es.wokis.player.engine.model.PlayerSnapshot
import es.wokis.player.engine.model.PlayerTrack
import es.wokis.services.lavaplayer.GuildLavaPlayerService
import es.wokis.services.localization.LocalizationService
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import mock.TestDispatchers
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class GuildLavaPlayerServiceTest {

    private val appDispatchers = TestDispatchers()
    private val textChannel: MessageChannel = mockk()
    private val voiceChannel: BaseVoiceChannelBehavior = mockk()
    private val playerEngine: PlayerEngine = mockk {
        every { events } returns emptyFlow()
    }
    private val localizationService: LocalizationService = mockk()

    private val playerService = GuildLavaPlayerService(
        appDispatchers = appDispatchers,
        textChannel = textChannel,
        voiceChannel = voiceChannel,
        playerEngine = playerEngine,
        localizationService = localizationService,
        guildId = Snowflake(123)
    )

    @Test
    fun `Given a snapshot When queue and current track are requested Then the engine snapshot is mapped`() {
        // Given
        val current = track("current")
        val queued = track("queued")
        every { playerEngine.snapshot() } returns PlayerSnapshot(current = current, queue = listOf(queued), paused = true)
        every { playerEngine.isPaused() } returns true

        // When / Then
        assertEquals(listOf(queued), playerService.getQueue())
        assertSame(current, playerService.getCurrentPlayingTrack())
        assertTrue(playerService.isPaused())
    }

    @Test
    fun `Given a paused engine When pause and resume run Then the engine is delegated`() = runTest {
        // Given
        coJustRun { playerEngine.pause() }
        coJustRun { playerEngine.resume() }

        // When
        playerService.pause()
        playerService.resume()

        // Then
        coVerify(exactly = 1) { playerEngine.pause() }
        coVerify(exactly = 1) { playerEngine.resume() }
    }

    @Test
    fun `Given a queue When skip shuffle and stop run Then the engine is delegated`() = runTest {
        // Given
        coJustRun { playerEngine.skip() }
        coJustRun { playerEngine.shuffle() }
        coJustRun { playerEngine.stop() }

        // When
        playerService.skip()
        playerService.shuffle()
        playerService.stop()

        // Then
        coVerify(exactly = 1) { playerEngine.skip() }
        coVerify(exactly = 1) { playerEngine.shuffle() }
        coVerify(exactly = 1) { playerEngine.stop() }
    }

    @Test
    fun `Given a load request When loadAndPlay runs Then the engine receives the same url`() {
        // Given
        coJustRun { playerEngine.loadAndPlay(any(), any()) }

        // When
        playerService.loadAndPlay("https://example.test/track", addToFront = true)

        // Then
        coVerify(exactly = 1) { playerEngine.loadAndPlay("https://example.test/track", true) }
    }

    @Test
    fun `Given a queue search When moveTrackToNext runs Then the engine result is returned`() = runTest {
        // Given
        val moved = track("moved")
        coEvery { playerEngine.moveTrackToNext("moved") } returns moved

        // When
        val actual = playerService.moveTrackToNext("moved")

        // Then
        assertSame(moved, actual)
    }

    @Test
    fun `Given an empty engine When queue emptiness is requested Then the engine answer is used`() {
        // Given
        every { playerEngine.isQueueEmpty() } returns true
        every { playerEngine.snapshot() } returns PlayerSnapshot()

        // Then
        assertTrue(playerService.isQueueEmpty())
        assertFalse(playerService.isConnected())
        assertNull(playerService.getCurrentPlayingTrack())
    }

    private fun track(identifier: String): PlayerTrack = PlayerTrack(
        identifier = identifier,
        title = identifier,
        author = "author",
    )
}
