package architecture

import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private val FORBIDDEN_VENDOR_PREFIXES: List<List<String>> =
    VendorFamily.entries.map { vendor -> vendor.prefixSegments } + listOf(
        listOf("com", "sedmelluq", "discord", "lavaplayer"),
        listOf("com", "github", "topi314", "lavasrc"),
        listOf("org", "koin"),
    )

class ModuleGatewayImportTest {

    @Test
    fun `Given the commands module sources When scanned Then they import no gateway or audio vendor`() {
        val roots = listOf(
            repositoryRoot().resolve("commands/src/main/kotlin"),
            repositoryRoot().resolve("commands/src/test/kotlin"),
        )

        val imports = roots.flatMap { root ->
            Files.walk(root).use { paths ->
                paths
                    .filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".kt") }
                    .flatMap { path -> KotlinFileHeaderParser.parse(Files.readString(path)).imports.stream() }
                    .toList()
            }
        }

        assertTrue(imports.isNotEmpty(), "the commands sources must have been scanned")
        imports.forEach { imported ->
            FORBIDDEN_VENDOR_PREFIXES.forEach { prefix ->
                assertFalse(
                    imported.startsWithSegments(prefix),
                    "the gateway-free commands module imported '${imported.statement}'",
                )
            }
        }
    }

    @Test
    fun `Given the configuration module sources When scanned Then they import no gateway or audio vendor`() {
        val roots = listOf(
            repositoryRoot().resolve("configuration/src/main/kotlin"),
            repositoryRoot().resolve("configuration/src/test/kotlin"),
        )

        val imports = roots.flatMap { root ->
            Files.walk(root).use { paths ->
                paths
                    .filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".kt") }
                    .flatMap { path -> KotlinFileHeaderParser.parse(Files.readString(path)).imports.stream() }
                    .toList()
            }
        }

        assertTrue(imports.isNotEmpty(), "the configuration sources must have been scanned")
        imports.forEach { imported ->
            FORBIDDEN_VENDOR_PREFIXES.forEach { prefix ->
                assertFalse(
                    imported.startsWithSegments(prefix),
                    "the framework-independent configuration module imported '${imported.statement}'",
                )
            }
        }
    }

    private fun repositoryRoot(): Path {
        var current: Path? = Paths.get("").toAbsolutePath()
        while (current != null) {
            if (Files.exists(current.resolve("settings.gradle.kts")) &&
                Files.isDirectory(current.resolve("src/test/kotlin"))
            ) {
                return current
            }
            current = current.parent
        }
        error("Could not locate the repository root from ${Paths.get("").toAbsolutePath()}")
    }
}
