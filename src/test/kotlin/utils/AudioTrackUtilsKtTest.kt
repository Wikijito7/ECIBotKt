package utils

import es.wokis.services.lavaplayer.model.TrackBO
import es.wokis.utils.getDisplayTrackName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class AudioTrackUtilsKtTest {

    @Test
    fun `Given track When getDisplayName is called Then return track name`() {
        // Given
        val trackBO = TrackBO(
            identifier = "manolete",
            title = "manolete",
            author = "paco",
        )
        val expected = "paco - manolete"

        // When
        val actual = trackBO.getDisplayTrackName()

        // Then
        assertEquals(expected, actual)
    }

    @Test
    fun `Given track with duplicated author When getDisplayName is called Then return track name`() {
        // Given
        val trackBO = TrackBO(
            identifier = "pepe",
            title = "pepe - manolete",
            author = "paco",
        )
        val expected = "pepe - manolete"

        // When
        val actual = trackBO.getDisplayTrackName()

        // Then
        assertEquals(expected, actual)
    }

    @Test
    fun `Given track with custom name When getDisplayName is called Then return custom name`() {
        // Given
        val trackBO = TrackBO(
            identifier = "manolete",
            title = "manolete",
            author = "paco",
            customName = "Custom Radio Name",
        )
        val expected = "Custom Radio Name"

        // When
        val actual = trackBO.getDisplayTrackName()

        // Then
        assertEquals(expected, actual)
    }
}
