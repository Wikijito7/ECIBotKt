package architecture

import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SourceTreeScannerTest {

    @Test
    fun `Given the current Phase 2 repository When scanned Then there are no violations and every source root is classified`() {
        val config = ImportBoundaryConfigs.phase2()
        val scanner = SourceTreeScanner(repositoryRoot(), config)

        val report = scanner.scan()

        assertTrue(report.scannedSourceFiles > 100, report.failureSummary())
        assertEquals(emptyList(), report.completenessProblems, report.failureSummary())
        assertEquals(emptyList(), report.violations, report.failureSummary())
        assertTrue(report.isClean, report.failureSummary())
    }

    @Test
    fun `Given the configuration module sources When scanned Then they import no gateway or audio vendor`() {
        val roots = listOf(
            repositoryRoot().resolve("configuration/src/main/kotlin"),
            repositoryRoot().resolve("configuration/src/test/kotlin"),
        )
        val forbiddenPrefixes = listOf(
            listOf("dev", "kord"),
            listOf("net", "dv8tion"),
            listOf("dev", "minn", "jda", "ktx"),
            listOf("moe", "kyokobot", "libdave"),
            listOf("com", "sedmelluq", "discord", "lavaplayer"),
            listOf("com", "github", "topi314", "lavasrc"),
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
            forbiddenPrefixes.forEach { prefix ->
                assertFalse(
                    imported.startsWithSegments(prefix),
                    "the framework-independent configuration module imported '${imported.statement}'",
                )
            }
        }
    }

    @Test
    fun `Given a real header import and fake import text When scanned Then only the real import is a violation`(
        @TempDir tempDir: Path
    ) {
        writeKotlinFile(
            tempDir,
            "app/src/main/kotlin/Foo.kt",
            """
            package es.wokis.core

            // import net.dv8tion.jda.api.JDA
            /* import dev.kord.core.Kord */
            import net.dv8tion.jda.api.JDA

            class Foo
            """.trimIndent(),
        )
        val config = ImportBoundaryConfig(
            mode = ScanMode.PHASE_2,
            roots = listOf(SourceRootRule("app/src/main/kotlin", SourceModule.CORE)),
        )

        val report = SourceTreeScanner(tempDir, config).scan()

        assertTrue(report.completenessProblems.isEmpty(), report.failureSummary())
        assertEquals(1, report.violations.size)
        assertEquals("app/src/main/kotlin/Foo.kt", report.violations.single().file)
        assertEquals("net.dv8tion.jda.api.JDA", report.violations.single().importStatement)
        assertEquals(VendorFamily.JDA, report.violations.single().vendor)
    }

    @Test
    fun `Given mismatched filesystem path and package When scanned Then the header package decides adapter allowance`(
        @TempDir tempDir: Path
    ) {
        writeKotlinFile(
            tempDir,
            "jda-runtime/src/main/kotlin/not/the/path/Foo.kt",
            "package es.wokis.jda.command\nimport net.dv8tion.jda.api.JDA\n",
        )
        writeKotlinFile(
            tempDir,
            "jda-runtime/src/main/kotlin/es/wokis/jda/Bar.kt",
            "package other.pkg\nimport net.dv8tion.jda.api.JDA\n",
        )
        writeKotlinFile(
            tempDir,
            "jda-runtime/src/main/kotlin/NoPackage.kt",
            "import net.dv8tion.jda.api.JDA\n",
        )
        val config = ImportBoundaryConfig(
            mode = ScanMode.PHASE_2,
            roots = listOf(SourceRootRule("jda-runtime/src/main/kotlin", SourceModule.JDA_RUNTIME)),
        )

        val report = SourceTreeScanner(tempDir, config).scan()

        assertEquals(
            listOf(
                "jda-runtime/src/main/kotlin/NoPackage.kt",
                "jda-runtime/src/main/kotlin/es/wokis/jda/Bar.kt",
            ),
            report.violations.map { it.file },
        )
        assertEquals(listOf(null, "other.pkg"), report.violations.map { it.packageName })
    }

    @Test
    fun `Given an unclassified Kotlin source root When scanned Then completeness fails until the root is configured`(
        @TempDir tempDir: Path
    ) {
        writeKotlinFile(tempDir, "known/src/main/kotlin/Known.kt", "package known\n")
        writeKotlinFile(tempDir, "unknown/src/main/kotlin/Unknown.kt", "package unknown\n")
        val knownRoot = SourceRootRule("known/src/main/kotlin", SourceModule.ROOT)

        val before = SourceTreeScanner(
            repositoryRoot = tempDir,
            config = ImportBoundaryConfig(ScanMode.PHASE_2, listOf(knownRoot)),
        ).scan()

        assertFalse(before.isClean)
        assertEquals(1, before.completenessProblems.size)
        assertEquals("unknown/src/main/kotlin/Unknown.kt", before.completenessProblems.single().file)
        assertEquals("unknown/src/main/kotlin", before.completenessProblems.single().derivedRoot)
        assertTrue(before.completenessProblems.single().message.contains("unknown/src/main/kotlin"))

        val after = SourceTreeScanner(
            repositoryRoot = tempDir,
            config = ImportBoundaryConfig(
                ScanMode.PHASE_2,
                listOf(knownRoot, SourceRootRule("unknown/src/main/kotlin", SourceModule.CORE)),
            ),
        ).scan()

        assertTrue(after.completenessProblems.isEmpty(), after.failureSummary())
        assertTrue(after.isClean, after.failureSummary())
    }

    @Test
    fun `Given build outputs and Gradle scripts When scanned Then they are outside the source universe`(
        @TempDir tempDir: Path
    ) {
        writeKotlinFile(tempDir, "app/src/main/kotlin/Valid.kt", "package app\n")
        writeKotlinFile(
            tempDir,
            "app/build/generated/Generated.kt",
            "package generated\nimport net.dv8tion.jda.api.JDA\n",
        )
        writeKotlinFile(
            tempDir,
            "build/generated/Generated.kt",
            "package generated\nimport dev.kord.core.Kord\n",
        )
        writeKotlinFile(tempDir, ".gradle/cache/Cached.kt", "package cache\nimport moe.kyokobot.libdave.DaveSession\n")
        Files.writeString(tempDir.resolve("settings.gradle.kts"), "rootProject.name = \"fixture\"\n")
        Files.writeString(tempDir.resolve("build.gradle.kts"), "plugins {}\n")
        val config = ImportBoundaryConfig(
            mode = ScanMode.PHASE_2,
            roots = listOf(SourceRootRule("app/src/main/kotlin", SourceModule.ROOT)),
        )

        val report = SourceTreeScanner(tempDir, config).scan()

        assertTrue(report.completenessProblems.isEmpty(), report.failureSummary())
        assertTrue(report.violations.isEmpty(), report.failureSummary())
        assertTrue(report.isClean, report.failureSummary())
    }

    @Test
    fun `Given a build directory inside a source root When scanned Then it is scanned while real build outputs are ignored`(
        @TempDir tempDir: Path
    ) {
        writeKotlinFile(
            tempDir,
            "src/main/kotlin/example/build/Hidden.kt",
            "package example.build\nimport net.dv8tion.jda.api.JDA\n",
        )
        writeKotlinFile(
            tempDir,
            "build/classes/generated/Generated.kt",
            "package generated\nimport net.dv8tion.jda.api.JDA\n",
        )
        val config = ImportBoundaryConfig(
            mode = ScanMode.PHASE_2,
            roots = listOf(SourceRootRule("src/main/kotlin", SourceModule.ROOT)),
        )

        val report = SourceTreeScanner(tempDir, config).scan()

        assertTrue(report.completenessProblems.isEmpty(), report.failureSummary())
        assertEquals(1, report.scannedSourceFiles)
        assertEquals(1, report.violations.size)
        assertEquals("src/main/kotlin/example/build/Hidden.kt", report.violations.single().file)
        assertEquals(VendorFamily.JDA, report.violations.single().vendor)
    }

    @Test
    fun `Given a build package under a custom src root When scanned Then it is never pruned and fails closed until configured`(
        @TempDir tempDir: Path
    ) {
        writeKotlinFile(
            tempDir,
            "app/src/kotlin/example/build/Hidden.kt",
            "package example.build\n",
        )
        writeKotlinFile(
            tempDir,
            "app/build/classes/generated/Generated.kt",
            "package generated\nimport net.dv8tion.jda.api.JDA\n",
        )
        val unconfigured = ImportBoundaryConfig(
            mode = ScanMode.PHASE_2,
            roots = listOf(SourceRootRule("app/src/main/kotlin", SourceModule.ROOT)),
        )

        val before = SourceTreeScanner(tempDir, unconfigured).scan()

        assertFalse(before.isClean, before.failureSummary())
        assertEquals(0, before.scannedSourceFiles)
        assertEquals(1, before.completenessProblems.size)
        assertEquals("app/src/kotlin/example/build/Hidden.kt", before.completenessProblems.single().file)
        assertTrue(before.violations.isEmpty(), before.failureSummary())

        val configured = ImportBoundaryConfig(
            mode = ScanMode.PHASE_2,
            roots = listOf(
                SourceRootRule("app/src/main/kotlin", SourceModule.ROOT),
                SourceRootRule("app/src/kotlin", SourceModule.ROOT),
            ),
        )
        val after = SourceTreeScanner(tempDir, configured).scan()

        assertTrue(after.completenessProblems.isEmpty(), after.failureSummary())
        assertEquals(1, after.scannedSourceFiles)
        assertTrue(after.isClean, after.failureSummary())
    }

    @Test
    fun `Given a configured custom Kotlin source root When scanned Then its build package is scanned and the vendor import is detected`(
        @TempDir tempDir: Path
    ) {
        writeKotlinFile(
            tempDir,
            "app/kotlin/example/build/Hidden.kt",
            "package example.build\nimport net.dv8tion.jda.api.JDA\n",
        )
        writeKotlinFile(
            tempDir,
            "app/build/classes/generated/Generated.kt",
            "package generated\nimport net.dv8tion.jda.api.JDA\n",
        )
        val config = ImportBoundaryConfig(
            mode = ScanMode.PHASE_2,
            roots = listOf(SourceRootRule("app/kotlin", SourceModule.ROOT)),
        )

        val report = SourceTreeScanner(tempDir, config).scan()

        assertTrue(report.completenessProblems.isEmpty(), report.failureSummary())
        assertEquals(1, report.scannedSourceFiles)
        assertEquals(1, report.violations.size)
        assertEquals("app/kotlin/example/build/Hidden.kt", report.violations.single().file)
        assertEquals(VendorFamily.JDA, report.violations.single().vendor)
    }

    @Test
    fun `Given an unconfigured custom Kotlin source root with a build package When scanned Then completeness fails closed`(
        @TempDir tempDir: Path
    ) {
        writeKotlinFile(
            tempDir,
            "app/kotlin/example/build/Hidden.kt",
            "package example.build\n",
        )
        val config = ImportBoundaryConfig(
            mode = ScanMode.PHASE_2,
            roots = emptyList(),
        )

        val report = SourceTreeScanner(tempDir, config).scan()

        assertFalse(report.isClean, report.failureSummary())
        assertEquals(0, report.scannedSourceFiles)
        assertEquals(1, report.completenessProblems.size)
        assertEquals("app/kotlin/example/build/Hidden.kt", report.completenessProblems.single().file)
    }

    @Test
    fun `Given same line semicolon separated imports When scanned Then the second vendor import is a violation`(
        @TempDir tempDir: Path
    ) {
        writeKotlinFile(
            tempDir,
            "src/main/kotlin/Semicolon.kt",
            "package demo\nimport ok.Type; import net.dv8tion.jda.api.JDA\n",
        )
        val config = ImportBoundaryConfig(
            mode = ScanMode.PHASE_2,
            roots = listOf(SourceRootRule("src/main/kotlin", SourceModule.ROOT)),
        )

        val report = SourceTreeScanner(tempDir, config).scan()

        assertTrue(report.completenessProblems.isEmpty(), report.failureSummary())
        assertEquals(1, report.scannedSourceFiles)
        assertEquals(1, report.violations.size)
        val violation = report.violations.single()
        assertEquals("src/main/kotlin/Semicolon.kt", violation.file)
        assertEquals("net.dv8tion.jda.api.JDA", violation.importStatement)
        assertEquals(2, violation.line)
        assertEquals(VendorFamily.JDA, violation.vendor)
    }

    @Test
    fun `Given a root project build script When scanned Then root build output is pruned`(@TempDir tempDir: Path) {
        Files.writeString(tempDir.resolve("build.gradle.kts"), "plugins {}\n")
        writeKotlinFile(
            tempDir,
            "build/classes/generated/Generated.kt",
            "package generated\nimport net.dv8tion.jda.api.JDA\n",
        )
        val config = ImportBoundaryConfig(
            mode = ScanMode.PHASE_2,
            roots = emptyList(),
        )

        val report = SourceTreeScanner(tempDir, config).scan()

        assertTrue(report.completenessProblems.isEmpty(), report.failureSummary())
        assertTrue(report.violations.isEmpty(), report.failureSummary())
        assertEquals(0, report.scannedSourceFiles)
        assertTrue(report.isClean, report.failureSummary())
    }

    @Test
    fun `Given a temporary non-project root with a build directory When scanned Then its Kotlin is not silently pruned`(
        @TempDir tempDir: Path
    ) {
        writeKotlinFile(
            tempDir,
            "build/classes/generated/Generated.kt",
            "package generated\nimport net.dv8tion.jda.api.JDA\n",
        )
        val config = ImportBoundaryConfig(
            mode = ScanMode.PHASE_2,
            roots = emptyList(),
        )

        val report = SourceTreeScanner(tempDir, config).scan()

        assertFalse(report.isClean, report.failureSummary())
        assertTrue(report.violations.isEmpty(), report.failureSummary())
        assertEquals(0, report.scannedSourceFiles)
        assertEquals(1, report.completenessProblems.size)
        assertEquals("build/classes/generated/Generated.kt", report.completenessProblems.single().file)
    }

    @Test
    fun `Given a custom output directory When scanned Then it fails closed unless explicitly registered`(
        @TempDir tempDir: Path
    ) {
        writeKotlinFile(tempDir, "app/src/main/kotlin/Valid.kt", "package app\n")
        writeKotlinFile(
            tempDir,
            "app/custom-output/Generated.kt",
            "package generated\nimport net.dv8tion.jda.api.JDA\n",
        )
        val baseConfig = ImportBoundaryConfig(
            mode = ScanMode.PHASE_2,
            roots = listOf(SourceRootRule("app/src/main/kotlin", SourceModule.ROOT)),
        )

        val before = SourceTreeScanner(tempDir, baseConfig).scan()

        assertFalse(before.isClean, before.failureSummary())
        assertTrue(before.violations.isEmpty(), before.failureSummary())
        assertEquals(1, before.scannedSourceFiles)
        assertEquals(1, before.completenessProblems.size)
        assertEquals("app/custom-output/Generated.kt", before.completenessProblems.single().file)

        val registeredConfig = baseConfig.copy(excludedOutputRoots = listOf("app/custom-output"))
        val after = SourceTreeScanner(tempDir, registeredConfig).scan()

        assertTrue(after.completenessProblems.isEmpty(), after.failureSummary())
        assertTrue(after.violations.isEmpty(), after.failureSummary())
        assertEquals(1, after.scannedSourceFiles)
        assertTrue(after.isClean, after.failureSummary())
    }

    @Test
    fun `Given nested project spike and cache outputs When scanned Then traversal prunes them`(@TempDir tempDir: Path) {
        writeKotlinFile(tempDir, "app/src/main/kotlin/Valid.kt", "package app\n")
        writeKotlinFile(
            tempDir,
            "services/api/build/classes/Generated.kt",
            "package generated\nimport net.dv8tion.jda.api.JDA\n",
        )
        writeKotlinFile(
            tempDir,
            "spikes/jda-dave/build/generated/Generated.kt",
            "package generated\nimport net.dv8tion.jda.api.JDA\n",
        )
        writeKotlinFile(tempDir, "app/.gradle/cache/Cached.kt", "package cache\nimport dev.kord.core.Kord\n")
        writeKotlinFile(tempDir, "app/.git/objects/Secret.kt", "package git\nimport moe.kyokobot.libdave.DaveSession\n")
        writeKotlinFile(tempDir, "services/api/build.gradle.kts", "plugins {}\n")
        writeKotlinFile(tempDir, "spikes/jda-dave/build.gradle.kts", "plugins {}\n")
        Files.writeString(tempDir.resolve("app/build.gradle.kts"), "plugins {}\n")
        val config = ImportBoundaryConfig(
            mode = ScanMode.PHASE_2,
            roots = listOf(SourceRootRule("app/src/main/kotlin", SourceModule.ROOT)),
        )

        val report = SourceTreeScanner(tempDir, config).scan()

        assertTrue(report.completenessProblems.isEmpty(), report.failureSummary())
        assertTrue(report.violations.isEmpty(), report.failureSummary())
        assertEquals(1, report.scannedSourceFiles)
        assertTrue(report.isClean, report.failureSummary())
    }

    @Test
    fun `Given an overlapping scanned and excluded root When scanned Then the configuration fails closed`(
        @TempDir tempDir: Path
    ) {
        writeKotlinFile(tempDir, "src/main/kotlin/Overlap.kt", "package overlap\n")
        val overlappingExclusions = listOf(
            "src/main/kotlin",
            "src/main/kotlin/generated",
            "src",
        )

        overlappingExclusions.forEach { excludedRoot ->
            val config = ImportBoundaryConfig(
                mode = ScanMode.PHASE_2,
                roots = listOf(SourceRootRule("src/main/kotlin", SourceModule.ROOT)),
                excludedRoots = listOf(excludedRoot),
            )

            val report = SourceTreeScanner(tempDir, config).scan()

            assertFalse(report.isClean, "Expected overlap with '$excludedRoot' to fail closed")
            assertTrue(
                report.completenessProblems.any {
                    it.message.contains("overlaps excluded root '$excludedRoot'")
                },
                report.failureSummary(),
            )
        }
    }

    @Test
    fun `Given an excluded output root overlapping a scanned root When scanned Then both phases fail closed`(
        @TempDir tempDir: Path
    ) {
        writeKotlinFile(tempDir, "app/kotlin/Overlap.kt", "package overlap\n")
        val overlappingOutputRoots = listOf(
            "app/kotlin",
            "app",
            "app/kotlin/generated",
        )

        listOf(ScanMode.PHASE_2, ScanMode.PHASE_6).forEach { mode ->
            overlappingOutputRoots.forEach { outputRoot ->
                val config = ImportBoundaryConfig(
                    mode = mode,
                    roots = listOf(SourceRootRule("app/kotlin", SourceModule.ROOT)),
                    excludedOutputRoots = listOf(outputRoot),
                )

                val report = SourceTreeScanner(tempDir, config).scan()

                assertFalse(report.isClean, "Expected $mode overlap with '$outputRoot' to fail closed")
                assertTrue(
                    report.completenessProblems.any {
                        it.message.contains("overlaps excluded output root '$outputRoot'")
                    },
                    report.failureSummary(),
                )
            }
        }
    }

    @Test
    fun `Given normal Phase 2 and Phase 6 configurations When scanned Then no overlap diagnostic is produced`(
        @TempDir tempDir: Path
    ) {
        val configurations = listOf(ImportBoundaryConfigs.phase2(), ImportBoundaryConfigs.phase6())

        configurations.forEach { config ->
            val report = SourceTreeScanner(tempDir, config).scan()

            assertTrue(report.completenessProblems.none { it.message.contains("overlaps") }, report.failureSummary())
            assertTrue(report.isClean, report.failureSummary())
        }
    }

    @Test
    fun `Given the spike source root When scanning Phase 2 and Phase 6 Then exclusion and spike allowance differ`(
        @TempDir tempDir: Path
    ) {
        writeKotlinFile(
            tempDir,
            "spikes/jda-dave/src/main/kotlin/spike/jdadave/Voice.kt",
            "package spike.jdadave\nimport net.dv8tion.jda.api.JDA\n",
        )
        writeKotlinFile(
            tempDir,
            "spikes/jda-dave/src/main/kotlin/spike/other/Other.kt",
            "package spike.other\nimport net.dv8tion.jda.api.JDA\n",
        )

        val phase2 = SourceTreeScanner(tempDir, ImportBoundaryConfigs.phase2()).scan()

        assertTrue(phase2.isClean, phase2.failureSummary())

        val phase6 = SourceTreeScanner(tempDir, ImportBoundaryConfigs.phase6()).scan()

        assertTrue(phase6.completenessProblems.isEmpty(), phase6.failureSummary())
        assertEquals(1, phase6.violations.size)
        assertEquals("spikes/jda-dave/src/main/kotlin/spike/other/Other.kt", phase6.violations.single().file)
    }

    @Test
    fun `Given a symlinked Kotlin file pointing outside the repository When scanned Then it is rejected unread`(
        @TempDir repositoryDir: Path,
        @TempDir externalDir: Path,
    ) {
        val externalFile = externalDir.resolve("Evil.kt")
        Files.writeString(externalFile, "package es.wokis.core\nimport net.dv8tion.jda.api.JDA\n")
        val sourceRoot = repositoryDir.resolve("app/src/main/kotlin")
        Files.createDirectories(sourceRoot)
        createSymbolicLinkOrSkip(sourceRoot.resolve("Linked.kt"), externalFile)
        val config = ImportBoundaryConfig(
            mode = ScanMode.PHASE_2,
            roots = listOf(SourceRootRule("app/src/main/kotlin", SourceModule.CORE)),
        )

        val report = SourceTreeScanner(repositoryDir, config).scan()

        assertFalse(report.isClean, report.failureSummary())
        assertTrue(report.violations.isEmpty(), report.failureSummary())
        assertEquals(0, report.scannedSourceFiles)
        assertEquals(1, report.completenessProblems.size)
        assertEquals("app/src/main/kotlin/Linked.kt", report.completenessProblems.single().file)
        assertTrue(report.completenessProblems.single().message.contains("Symlinked Kotlin source file"))
    }

    @Test
    fun `Given a symlinked configured source root When scanned Then it is rejected unread`(
        @TempDir repositoryDir: Path,
        @TempDir externalDir: Path,
    ) {
        val externalRoot = externalDir.resolve("kotlin")
        Files.createDirectories(externalRoot)
        Files.writeString(externalRoot.resolve("Evil.kt"), "package es.wokis.core\nimport net.dv8tion.jda.api.JDA\n")
        val sourceRootParent = repositoryDir.resolve("app/src")
        Files.createDirectories(sourceRootParent)
        createSymbolicLinkOrSkip(sourceRootParent.resolve("kotlin"), externalRoot)
        val config = ImportBoundaryConfig(
            mode = ScanMode.PHASE_2,
            roots = listOf(SourceRootRule("app/src/kotlin", SourceModule.CORE)),
        )

        val report = SourceTreeScanner(repositoryDir, config).scan()

        assertFalse(report.isClean, report.failureSummary())
        assertTrue(report.violations.isEmpty(), report.failureSummary())
        assertEquals(0, report.scannedSourceFiles)
        assertEquals(1, report.completenessProblems.size)
        assertEquals("app/src/kotlin", report.completenessProblems.single().file)
        assertTrue(report.completenessProblems.single().message.contains("symbolic link"))
    }

    @Test
    fun `Given a symlinked directory inside a scanned root When scanned Then it is not followed`(
        @TempDir repositoryDir: Path,
        @TempDir externalDir: Path,
    ) {
        Files.writeString(externalDir.resolve("Evil.kt"), "package es.wokis.core\nimport net.dv8tion.jda.api.JDA\n")
        val sourceRoot = repositoryDir.resolve("app/src/main/kotlin")
        Files.createDirectories(sourceRoot)
        createSymbolicLinkOrSkip(sourceRoot.resolve("linked"), externalDir)
        val config = ImportBoundaryConfig(
            mode = ScanMode.PHASE_2,
            roots = listOf(SourceRootRule("app/src/main/kotlin", SourceModule.CORE)),
        )

        val report = SourceTreeScanner(repositoryDir, config).scan()

        assertFalse(report.isClean, report.failureSummary())
        assertTrue(report.violations.isEmpty(), report.failureSummary())
        assertEquals(0, report.scannedSourceFiles)
        assertEquals(1, report.completenessProblems.size)
        assertEquals("app/src/main/kotlin/linked", report.completenessProblems.single().file)
        assertTrue(report.completenessProblems.single().message.contains("Symlinked directory"))
    }

    @Test
    fun `Given violations in several files When scanned Then the report is deterministically ordered`(
        @TempDir tempDir: Path
    ) {
        writeKotlinFile(
            tempDir,
            "core/src/main/kotlin/Zeta.kt",
            "package es.wokis.core\nimport dev.kord.core.Kord\nimport net.dv8tion.jda.api.JDA\n",
        )
        writeKotlinFile(
            tempDir,
            "core/src/main/kotlin/Alpha.kt",
            "package es.wokis.core\nimport moe.kyokobot.libdave.DaveSession\n",
        )

        val report = SourceTreeScanner(tempDir, ImportBoundaryConfigs.phase2()).scan()

        assertEquals(
            listOf(
                "core/src/main/kotlin/Alpha.kt",
                "core/src/main/kotlin/Zeta.kt",
                "core/src/main/kotlin/Zeta.kt",
            ),
            report.violations.map { it.file },
        )
        assertEquals(
            listOf(VendorFamily.LIBDAVE, VendorFamily.KORD, VendorFamily.JDA),
            report.violations.map { it.vendor },
        )
    }

    private fun writeKotlinFile(directory: Path, relativePath: String, content: String) {
        val file = directory.resolve(relativePath)
        Files.createDirectories(file.parent)
        Files.writeString(file, content)
    }

    private fun createSymbolicLinkOrSkip(link: Path, target: Path) {
        try {
            Files.createSymbolicLink(link, target)
        } catch (exception: UnsupportedOperationException) {
            Assumptions.assumeTrue(false, "Symbolic links are unsupported on this platform: ${exception.message}")
        } catch (exception: IOException) {
            Assumptions.assumeTrue(false, "Symbolic links cannot be created in this environment: ${exception.message}")
        } catch (exception: SecurityException) {
            Assumptions.assumeTrue(false, "Symbolic links are forbidden in this environment: ${exception.message}")
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
