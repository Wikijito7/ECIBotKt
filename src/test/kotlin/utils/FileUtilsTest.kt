package utils

import es.wokis.utils.getOrCreateFile
import es.wokis.utils.updateFile
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val MOCKED_PATH = "./data-test/"
private const val MOCKED_FILE_NAME = "test.json"
private val CONFIG_TEMPLATE = {}::class.java.getResourceAsStream("config-test.json")

class FileUtilsTest {

    @Test
    fun `Given file path When getOrCreate is called Then return file`() {
        // When
        val file = getOrCreateFile(MOCKED_PATH, MOCKED_FILE_NAME, CONFIG_TEMPLATE)

        // Then
        assertTrue(file.readLines().isNotEmpty())
    }

    @Test
    fun `Given file When updateFile is called Then update file`() {
        // Given
        val file = getOrCreateFile(MOCKED_PATH, MOCKED_FILE_NAME, CONFIG_TEMPLATE)
        val newData = mapOf("pepe" to "popo")

        // When
        file.updateFile(newData)

        // Then
        val newFile = getOrCreateFile(MOCKED_PATH, MOCKED_FILE_NAME, CONFIG_TEMPLATE)
        val newDataFromFile =
            Json.decodeFromString<Map<String, String>>(newFile.readLines().joinToString(separator = "") { it.trim() })
        assertEquals(newDataFromFile.size, newData.size)
        assertEquals(newData.entries, newDataFromFile.entries)
    }

    companion object {
        @JvmStatic
        @AfterAll
        fun tearDown() {
            File(MOCKED_PATH).deleteRecursively()
        }
    }
}
