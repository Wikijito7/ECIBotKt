package es.wokis.player.engine

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer
import es.wokis.player.engine.audio.PlayerAudioState
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import com.sedmelluq.discord.lavaplayer.track.playback.AudioFrame as LavaAudioFrame

class LavaPlayerAudioStreamTest {

    @Test
    fun `Given a paused player When next runs Then it returns silence and keeps polling provide zero`() {
        val player = mockk<AudioPlayer>()
        every { player.isPaused } returns true
        every { player.provide(0, TimeUnit.MILLISECONDS) } returns null
        val stream = LavaPlayerAudioStream(player) { false }

        val first = stream.next()
        val second = stream.next()

        assertEquals(PlayerAudioState.PausedSilence, first)
        assertEquals(PlayerAudioState.PausedSilence, second)
        verify(exactly = 2) { player.provide(0, TimeUnit.MILLISECONDS) }
    }

    @Test
    fun `Given an active player with a frame When next runs Then it returns that frame`() {
        val player = mockk<AudioPlayer>()
        val lavaFrame = mockk<LavaAudioFrame>()
        every { player.isPaused } returns false
        every { lavaFrame.data } returns byteArrayOf(0x78, 0x01)
        every { player.provide(0, TimeUnit.MILLISECONDS) } returns lavaFrame
        val stream = LavaPlayerAudioStream(player) { false }

        val actual = stream.next()

        assertTrue(actual is PlayerAudioState.Frame)
        assertEquals(listOf(0x78, 0x01), actual.frame.payloadCopy().map { it.toInt() and 0xFF })
    }

    @Test
    fun `Given an active player without a buffered frame When next runs Then it returns silence instead of ending`() {
        val player = mockk<AudioPlayer>()
        every { player.isPaused } returns false
        every { player.provide(0, TimeUnit.MILLISECONDS) } returns null
        val stream = LavaPlayerAudioStream(player) { false }

        val actual = stream.next()

        assertEquals(PlayerAudioState.PausedSilence, actual)
    }

    @Test
    fun `Given a terminated engine When next runs Then it returns ended without polling`() {
        val player = mockk<AudioPlayer>()
        val stream = LavaPlayerAudioStream(player) { true }

        val actual = stream.next()

        assertEquals(PlayerAudioState.Ended, actual)
        verify(exactly = 0) { player.provide(0, TimeUnit.MILLISECONDS) }
    }

    @Test
    fun `Given a closed stream When next runs Then it returns ended without polling the player`() {
        val player = mockk<AudioPlayer>()
        val stream = LavaPlayerAudioStream(player) { false }
        stream.close()

        val actual = stream.next()

        assertEquals(PlayerAudioState.Ended, actual)
        verify(exactly = 0) { player.provide(0, TimeUnit.MILLISECONDS) }
    }

    @Test
    fun `Given provide throws while the engine is active When next runs Then it reports once and ends`() {
        val player = mockk<AudioPlayer>()
        every { player.provide(0, TimeUnit.MILLISECONDS) } throws IllegalStateException("boom")
        val failures = mutableListOf<Throwable>()
        val stream = LavaPlayerAudioStream(player) { false }.apply { onFailure = { failures += it } }

        val first = stream.next()
        val second = stream.next()

        assertEquals(PlayerAudioState.Ended, first)
        assertEquals(PlayerAudioState.Ended, second)
        assertEquals(1, failures.size)
    }

    @Test
    fun `Given isPaused throws When next runs Then it reports one failure and ends`() {
        val player = mockk<AudioPlayer>()
        every { player.provide(0, TimeUnit.MILLISECONDS) } returns null
        every { player.isPaused } throws IllegalStateException("paused boom")
        val failures = mutableListOf<Throwable>()
        val stream = LavaPlayerAudioStream(player) { false }.apply { onFailure = { failures += it } }

        val first = stream.next()
        val second = stream.next()

        assertEquals(PlayerAudioState.Ended, first)
        assertEquals(PlayerAudioState.Ended, second)
        assertEquals(1, failures.size)
        assertEquals("paused boom", failures.single().message)
    }

    @Test
    fun `Given frame data throws When next runs Then it reports one failure and ends`() {
        val player = mockk<AudioPlayer>()
        val lavaFrame = mockk<LavaAudioFrame>()
        every { player.provide(0, TimeUnit.MILLISECONDS) } returns lavaFrame
        every { player.isPaused } returns false
        every { lavaFrame.data } throws IllegalStateException("data boom")
        val failures = mutableListOf<Throwable>()
        val stream = LavaPlayerAudioStream(player) { false }.apply { onFailure = { failures += it } }

        val first = stream.next()
        val second = stream.next()

        assertEquals(PlayerAudioState.Ended, first)
        assertEquals(PlayerAudioState.Ended, second)
        assertEquals(1, failures.size)
        assertEquals("data boom", failures.single().message)
    }

    @Test
    fun `Given provide throws while terminated When next runs Then it returns ended`() {
        val player = mockk<AudioPlayer>()
        every { player.provide(0, TimeUnit.MILLISECONDS) } throws IllegalStateException("boom")
        val stream = LavaPlayerAudioStream(player) { true }

        val actual = stream.next()

        assertEquals(PlayerAudioState.Ended, actual)
    }
}
