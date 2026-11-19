package es.wokis.jda.player

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.io.File

class JdaSoundFilesTest {

    private val audioFolder = File("./audio")
    private val catalog = JdaSoundsCatalogPort()

    @Test
    fun `Given a sound name When the path is requested Then append the fixed mp3 extension`() {
        assertEquals("./audio/rain.mp3", JdaSoundFiles.pathFor("rain"))
        assertEquals("./audio/Thunder.mp3", JdaSoundFiles.pathFor("Thunder"))
    }

    @Test
    fun `Given no audio folder When names are read Then reply empty`() {
        assumeTrue(!audioFolder.exists(), "the working directory already has an ./audio entry")

        assertTrue(JdaSoundFiles.names().isEmpty())
    }

    @Test
    fun `Given an audio entry that is a file When names are read Then reply empty`() {
        withAudioEntry { folder ->
            assertTrue(folder.createNewFile())

            assertTrue(JdaSoundFiles.names().isEmpty())
        }
    }

    @Test
    fun `Given an empty audio folder When names are read Then reply empty`() {
        withAudioEntry { folder ->
            assertTrue(folder.mkdirs())

            assertTrue(JdaSoundFiles.names().isEmpty())
        }
    }

    @Test
    fun `Given a populated audio folder When names are read Then strip every extension like Kord`() {
        withAudioEntry { folder ->
            assertTrue(folder.mkdirs())
            listOf("rain.mp3", "Thunder.MP3", "notes.txt", "noext").forEach { name ->
                assertTrue(File(folder, name).createNewFile())
            }

            assertEquals(setOf("rain", "Thunder", "notes", "noext"), JdaSoundFiles.names().toSet())
        }
    }

    @Test
    fun `Given a sound file When existence is probed Then match the exact name without extension`() {
        withAudioEntry { folder ->
            assertTrue(folder.mkdirs())
            assertTrue(File(folder, "Case.mp3").createNewFile())

            assertTrue(JdaSoundFiles.exists("Case"))
            assertFalse(
                JdaSoundFiles.exists("Case.mp3"),
                "the probe never accepts a name that already has the extension",
            )
            assertFalse(JdaSoundFiles.exists("missing"))
        }
    }

    @Test
    fun `Given a populated audio folder When the catalog port is read Then expose the same names and existence`() =
        runTest {
            withAudioEntry { folder ->
                assertTrue(folder.mkdirs())
                assertTrue(File(folder, "rain.mp3").createNewFile())

                assertEquals(listOf("rain"), catalog.soundNames())
                assertTrue(catalog.soundExists("rain"))
                assertFalse(catalog.soundExists("thunder"))
            }
        }

    private inline fun <T> withAudioEntry(block: (File) -> T): T {
        assumeTrue(!audioFolder.exists(), "the working directory already has an ./audio entry")
        try {
            return block(audioFolder)
        } finally {
            audioFolder.deleteRecursively()
        }
    }
}
