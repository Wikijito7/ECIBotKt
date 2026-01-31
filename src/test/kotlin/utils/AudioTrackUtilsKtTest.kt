package utils

import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo
import es.wokis.services.lavaplayer.model.TrackBO
import es.wokis.utils.getDisplayTrackName
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class AudioTrackUtilsKtTest {

    @Test
    fun `Given track When getDisplayName is called Then return track name`() {
        // Given
        val audioTrack: AudioTrack = mockk {
            every { info } returns AudioTrackInfo("manolete", "paco", 0, "", true, "", "", "")
        }
        val trackBO = TrackBO(audioTrack = audioTrack)
        val expected = "paco - manolete"

        // When
        val actual = trackBO.getDisplayTrackName()

        // Then
        assertEquals(expected, actual)
    }

    @Test
    fun `Given track with duplicated author When getDisplayName is called Then return track name`() {
        // Given
        val audioTrack: AudioTrack = mockk {
            every { info } returns AudioTrackInfo("pepe - manolete", "paco", 0, "", true, "", "", "")
        }
        val trackBO = TrackBO(audioTrack = audioTrack)
        val expected = "pepe - manolete"

        // When
        val actual = trackBO.getDisplayTrackName()

        // Then
        assertEquals(expected, actual)
    }

    @Test
    fun `Given track with custom name When getDisplayName is called Then return custom name`() {
        // Given
        val audioTrack: AudioTrack = mockk {
            every { info } returns AudioTrackInfo("manolete", "paco", 0, "", true, "", "", "")
        }
        val trackBO = TrackBO(customName = "Custom Radio Name", audioTrack = audioTrack)
        val expected = "Custom Radio Name"

        // When
        val actual = trackBO.getDisplayTrackName()

        // Then
        assertEquals(expected, actual)
    }
}
