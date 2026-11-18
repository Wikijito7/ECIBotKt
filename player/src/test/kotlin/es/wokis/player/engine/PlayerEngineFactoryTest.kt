package es.wokis.player.engine

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventListener
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

class PlayerEngineFactoryTest {

    @Test
    fun `Given a shared factory When engines close Then the manager closes only once from the factory`() {
        val manager: AudioPlayerManager = mockk()
        val firstPlayer: AudioPlayer = mockk()
        val secondPlayer: AudioPlayer = mockk()
        every { manager.createPlayer() } returns firstPlayer andThen secondPlayer
        every { firstPlayer.addListener(any<AudioEventListener>()) } just Runs
        every { secondPlayer.addListener(any<AudioEventListener>()) } just Runs
        every { firstPlayer.destroy() } just Runs
        every { secondPlayer.destroy() } just Runs
        every { manager.shutdown() } just Runs
        val factory = PlayerEngineFactory(manager)
        val firstEngine = factory.create()
        val secondEngine = factory.create()

        firstEngine.close()
        secondEngine.close()

        verify(exactly = 0) { manager.shutdown() }
        verify(exactly = 1) { firstPlayer.destroy() }
        verify(exactly = 1) { secondPlayer.destroy() }

        factory.close()
        factory.close()

        verify(exactly = 1) { manager.shutdown() }
    }
}
