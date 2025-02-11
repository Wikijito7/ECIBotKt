package utils

import es.wokis.utils.getOrCreateFile
import es.wokis.utils.updateFile
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val MOCKED_PATH = "./data-test/"
private const val MOCKED_FILE_NAME = "test.json"

class FileUtilsTest {

    @AfterEach
    fun tearDown() {
        File(MOCKED_PATH).deleteRecursively()
    }

    @Test
    fun `Given file path When getOrCreate is called without template Then return file`() {
        // When
        val file = getOrCreateFile(MOCKED_PATH, MOCKED_FILE_NAME)

        // Then
        assertTrue(file.readLines().isEmpty())
    }

    @Test
    fun `Given file path When getOrCreate is called Then return file`() {
        // Given
        val configTemplate = {}::class.java.getResourceAsStream("/template/config_template.json")

        // When
        val file = getOrCreateFile(MOCKED_PATH, MOCKED_FILE_NAME, configTemplate)

        // Then
        assertTrue(file.readLines().isNotEmpty())
    }

    @Test
    fun `Given file When updateFile is called Then update file`() {
        // Given
        val configTemplate = {}::class.java.getResourceAsStream("/template/config_template.json")
        val file = getOrCreateFile(MOCKED_PATH, MOCKED_FILE_NAME, configTemplate)
        val newData = mapOf("pepe" to "popo")

        // When
        file.updateFile(newData)

        // Then
        val newFile = getOrCreateFile(MOCKED_PATH, MOCKED_FILE_NAME, configTemplate)
        val newDataFromFile =
            Json.decodeFromString<Map<String, String>>(newFile.readLines().joinToString(separator = "") { it.trim() })
        assertEquals(newDataFromFile.size, newData.size)
        assertEquals(newData.entries, newDataFromFile.entries)
    }
}
