package utils

import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo
import es.wokis.utils.getDisplayTrackName
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class AudioTrackUtilsKtTest {

    @Test
    fun `Given track When getDisplayName is called Then return track name`() {
        // Given
        val track: AudioTrack = mockk<AudioTrack> {
            every { info } returns AudioTrackInfo("manolete", "paco", 0, "", true, "", "", "")
        }
        val expected = "paco - manolete"

        // When
        val actual = track.getDisplayTrackName()

        // Then
        assertEquals(expected, actual)
    }

    @Test
    fun `Given track with duplicated author When getDisplayName is called Then return track name`() {
        // Given
        val track: AudioTrack = mockk {
            every { info } returns AudioTrackInfo("pepe - manolete", "paco", 0, "", true, "", "", "")
        }
        val expected = "pepe - manolete"
        // When
        val actual = track.getDisplayTrackName()

        // Then
        assertEquals(expected, actual)
    }
}
