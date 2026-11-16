package es.wokis.utils

import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption

/*
 * Root-runtime file helpers.
 *
 * Ownership boundary after the shared `:configuration` extraction:
 *
 * - `:configuration`'s `ConfigLoader` owns seeding `./data/config.json` from
 *   `/template/config_template.json` (create only when missing, never rewrite an existing file).
 *   It does not use these helpers, so there is exactly one implementation of the config seam.
 * - `:locale-persistence`'s `LocalJsonLocaleRepository` keeps its own private, independent
 *   `getOrCreateFile` (create-if-missing, no template, lenient) so the shared adapter has no
 *   dependency on the root runtime and its longer-standing persisted-file semantics stay untouched.
 * - [getFolderContent] is still production code used by the root sound commands.
 * - [getOrCreateFile] and [updateFile] are legacy root helpers kept for their existing API and
 *   tests. Only the tests exercise them today; deleting them is a separate cleanup that must update
 *   those tests in the same change, and it is not part of the configuration path.
 */

/**
 * Tries to find given file path and name to return it. If this file is not found, it will create it and return
 * it with the given template, if given any, or empty otherwise.
 *
 * The shared configuration template is **not** seeded through this helper any more: `ConfigLoader`
 * in `:configuration` owns that path and has its own create-only-when-missing semantics.
 *
 * @param parent file parent path
 * @param fileName file name to be found or created
 * @param template template used to create the file if not found. If the template was not given, it will create an empty
 * file
 */
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
 *
 * Legacy root helper: no production call site remains after the configuration extraction; it is
 * retained for its existing API and tests (see the ownership boundary above the imports).
 */
inline fun <reified T> File.updateFile(value: T) {
    writer().apply {
        write(Json.encodeToString(value))
        close()
    }
}

/**
 * Tries to return all content of the given path.
 *
 * @param path path to the folder from which we want to get the content.
 *
 * @return list with all files if the path exists and is a folder, empty list otherwise.
 */
fun getFolderContent(path: String): List<File> {
    val folder = File(path)
    if (folder.exists().not() || folder.isFile) {
        return emptyList()
    }
    return folder.listFiles()?.toList().orEmpty()
}
