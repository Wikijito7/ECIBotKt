package es.wokis.utils

import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption

fun getOrCreateFile(parent: String, fileName: String, template: InputStream? = null): File {
    val file = File(parent, fileName)
    if (file.exists().not()) {
        file.parentFile.mkdirs()
        file.createNewFile()
        template?.let {
            Files.copy(template, Paths.get(file.path), StandardCopyOption.REPLACE_EXISTING)
        }
    }
    return file
}

/**
 * Serializes and writes given value inside given File
 *
 * Note: Serialization is made inside this function, you don't need to serialize it before writing
 */
inline fun <reified T> File.updateFile(value: T) {
    writer().apply {
        write(Json.encodeToString(value))
        close()
    }
}
