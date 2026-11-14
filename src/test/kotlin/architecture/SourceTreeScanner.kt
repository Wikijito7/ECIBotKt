package architecture

import java.nio.charset.StandardCharsets
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

data class CompletenessProblem(
    val file: String,
    val derivedRoot: String?,
    val message: String,
)

data class ImportScanReport(
    val scannedSourceFiles: Int,
    val violations: List<ImportViolation>,
    val completenessProblems: List<CompletenessProblem>,
) {
    val isClean: Boolean get() = violations.isEmpty() && completenessProblems.isEmpty()

    fun failureSummary(): String = buildString {
        append("Import boundary scan failed: ")
        append(violations.size)
        append(" violation(s), ")
        append(completenessProblems.size)
        append(" completeness problem(s) across ")
        append(scannedSourceFiles)
        append(" scanned Kotlin source file(s)")
        completenessProblems.forEach {
            appendLine()
            append("- completeness: ")
            append(it.message)
        }
        violations.forEach {
            appendLine()
            append("- violation: ")
            append(it.message)
        }
    }
}

/**
 * Enumerates every Kotlin `.kt` source file in the repository, excluding build
 * outputs and Gradle caches, and classifies each one exactly once against the
 * active scanner configuration. Unclassified roots fail closed.
 *
 * Symbolic links are never followed: a symlinked configured root, a symlinked
 * directory under a scanned root and any symlinked `.kt` file are completeness
 * problems, so external source can never be read through a link.
 */
class SourceTreeScanner(
    private val repositoryRoot: Path,
    private val config: ImportBoundaryConfig,
) {

    private val policy = ImportBoundaryPolicy(config.mode)
    private val excludedRoots = config.excludedRoots.sortedByDescending { it.length }
    private val rules = config.roots.sortedByDescending { it.path.length }

    fun scan(): ImportScanReport {
        val violations = mutableListOf<ImportViolation>()
        val completenessProblems = (configurationOverlapProblems() + configuredRootSymlinkProblems()).toMutableList()
        var scannedSourceFiles = 0

        repositoryEntries().forEach entryLoop@{ entry ->
            val relativePath = repositoryRoot.relativize(entry).toNormalizedPath()
            if (relativePath.isEmpty()) return@entryLoop
            if (excludedRoots.any { relativePath.isInside(it) }) return@entryLoop

            if (Files.isSymbolicLink(entry)) {
                val symlinkProblem = symlinkProblem(relativePath, entry) ?: return@entryLoop
                completenessProblems += symlinkProblem
                return@entryLoop
            }
            if (!Files.isRegularFile(entry) || !entry.fileName.toString().endsWith(".kt")) {
                return@entryLoop
            }

            val rule = rules.firstOrNull { relativePath.isInside(it.path) }
            if (rule == null) {
                val derivedRoot = deriveSourceRoot(relativePath)
                completenessProblems += CompletenessProblem(
                    file = relativePath,
                    derivedRoot = derivedRoot,
                    message = "Unclassified Kotlin source file '$relativePath' under " +
                        "'${derivedRoot ?: "<unknown source root>"}' in mode ${config.mode}; " +
                        "add that source root to the scanner configuration",
                )
                return@entryLoop
            }

            scannedSourceFiles++
            val header = KotlinFileHeaderParser.parse(Files.readString(entry, StandardCharsets.UTF_8))
            header.imports.forEach importLoop@{ imported ->
                val decision = policy.evaluate(rule.module, header.packageName, imported)
                val vendor = decision.vendor
                if (decision.allowed || vendor == null) return@importLoop
                violations += ImportViolation(
                    file = relativePath,
                    line = imported.line,
                    importStatement = imported.statement,
                    vendor = vendor,
                    module = rule.module,
                    mode = config.mode,
                    packageName = header.packageName,
                    reason = decision.reason,
                )
            }
        }

        return ImportScanReport(
            scannedSourceFiles = scannedSourceFiles,
            violations = violations.sortedWith(
                compareBy({ it.file }, { it.line }, { it.importStatement }, { it.vendor.name }),
            ),
            completenessProblems = completenessProblems.distinctBy { it.file to it.derivedRoot }.sortedBy { it.file },
        )
    }

    /**
     * Walks the repository without following symbolic links and prunes actual
     * Gradle output/cache directories during traversal. Output pruning is based
     * on the root project, configured source-root projects and discovered
     * `build.gradle[.kts]` projects, never on a path segment being named `build`.
     */
    private fun repositoryEntries(): List<Path> {
        if (!Files.isDirectory(repositoryRoot)) return emptyList()
        val entries = mutableListOf<Path>()
        val configuredRoots = config.roots.map { it.path }
        val explicitOutputRoots = config.excludedOutputRoots.sortedByDescending { it.length }
        val knownProjectRoots = mutableSetOf<String>()
        config.roots.mapNotNullTo(knownProjectRoots) { projectRootOf(it.path) }
        Files.walkFileTree(
            repositoryRoot,
            object : SimpleFileVisitor<Path>() {
                override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                    val relativePath = repositoryRoot.relativize(dir).toNormalizedPath()
                    if (relativePath.isNotEmpty() &&
                        relativePath.isPrunedOutputDirectory(
                            configuredRoots,
                            explicitOutputRoots,
                            knownProjectRoots,
                        )
                    ) {
                        return FileVisitResult.SKIP_SUBTREE
                    }
                    if (dir.hasGradleBuildFile()) {
                        knownProjectRoots.add(relativePath)
                    }
                    return FileVisitResult.CONTINUE
                }

                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    val fileName = file.fileName.toString()
                    if (fileName.endsWith(".kt") || Files.isSymbolicLink(file)) {
                        entries.add(file)
                    }
                    return FileVisitResult.CONTINUE
                }
            },
        )
        return entries.sortedBy { repositoryRoot.relativize(it).toNormalizedPath() }
    }

    private fun String.isPrunedOutputDirectory(
        configuredRoots: List<String>,
        explicitOutputRoots: List<String>,
        knownProjectRoots: Set<String>,
    ): Boolean {
        val name = substringAfterLast('/')
        if (name == ".gradle" || name == ".git") return true
        if (configuredRoots.any { isInside(it) || it.isInside(this) }) return false
        if (explicitOutputRoots.any { isInside(it) }) return true
        if (name != "build") return false
        return isActualProjectOutput(knownProjectRoots)
    }

    private fun String.isActualProjectOutput(knownProjectRoots: Set<String>): Boolean =
        knownProjectRoots.any { projectRoot ->
            if (projectRoot.isEmpty()) this == "build" else this == "$projectRoot/build"
        }

    private fun Path.hasGradleBuildFile(): Boolean =
        Files.isRegularFile(resolve("build.gradle.kts")) || Files.isRegularFile(resolve("build.gradle"))

    private fun projectRootOf(sourceRoot: String): String = when {
        sourceRoot.startsWith("src/") -> ""

        sourceRoot.contains("/src/") -> sourceRoot.substringBefore("/src/")

        else -> {
            val lastSlash = sourceRoot.lastIndexOf('/')
            if (lastSlash < 0) "" else sourceRoot.substring(0, lastSlash)
        }
    }

    private fun configurationOverlapProblems(): List<CompletenessProblem> =
        config.roots.flatMap { rule ->
            buildList {
                config.excludedRoots
                    .filter { excludedRoot -> rule.path.overlaps(excludedRoot) }
                    .forEach { excludedRoot ->
                        add(
                            CompletenessProblem(
                                file = rule.path,
                                derivedRoot = excludedRoot,
                                message = "Configured source root '${rule.path}' overlaps excluded root " +
                                    "'$excludedRoot'; the scanner configuration is ambiguous, so it fails closed",
                            ),
                        )
                    }
                config.excludedOutputRoots
                    .filter { outputRoot -> rule.path.overlaps(outputRoot) }
                    .forEach { outputRoot ->
                        add(
                            CompletenessProblem(
                                file = rule.path,
                                derivedRoot = outputRoot,
                                message = "Configured source root '${rule.path}' overlaps excluded output root " +
                                    "'$outputRoot'; the scanner configuration is ambiguous, so it fails closed",
                            ),
                        )
                    }
            }
        }.sortedBy { it.message }

    private fun String.overlaps(other: String): Boolean = isInside(other) || other.isInside(this)

    private fun configuredRootSymlinkProblems(): List<CompletenessProblem> =
        config.roots.mapNotNull { rule ->
            val symlinkPath = firstSymbolicLinkComponent(rule.path) ?: return@mapNotNull null
            CompletenessProblem(
                file = rule.path,
                derivedRoot = symlinkPath,
                message = "Configured source root '${rule.path}' contains symbolic link '$symlinkPath'; " +
                    "symbolic links are not followed",
            )
        }

    private fun firstSymbolicLinkComponent(relativeRoot: String): String? {
        var current = repositoryRoot
        val traversed = mutableListOf<String>()
        relativeRoot.split('/').forEach { segment ->
            current = current.resolve(segment)
            traversed += segment
            if (Files.isSymbolicLink(current)) {
                return traversed.joinToString("/")
            }
        }
        return null
    }

    private fun symlinkProblem(relativePath: String, entry: Path): CompletenessProblem? {
        if (entry.fileName.toString().endsWith(".kt")) {
            return CompletenessProblem(
                file = relativePath,
                derivedRoot = deriveSourceRoot(relativePath),
                message = "Symlinked Kotlin source file '$relativePath' is rejected before reading; " +
                    "symbolic links are not followed",
            )
        }
        if (!Files.isDirectory(entry)) return null
        val isInsideConfiguredRoot = rules.any { relativePath.isInside(it.path) }
        val isConfiguredRoot = config.roots.any { it.path == relativePath }
        if (!isInsideConfiguredRoot || isConfiguredRoot) return null
        return CompletenessProblem(
            file = relativePath,
            derivedRoot = relativePath,
            message = "Symlinked directory '$relativePath' under a scanned source root is not followed",
        )
    }

    private fun deriveSourceRoot(relativePath: String): String? {
        ROOT_SOURCE_ROOTS.firstOrNull { relativePath.isInside(it) }?.let { return it }
        val match = MODULE_SOURCE_ROOT_PATTERN.find(relativePath) ?: return null
        val module = match.groupValues[1]
        val sourceSet = match.groupValues[2]
        return "$module/src/$sourceSet/kotlin"
    }

    private fun Path.toNormalizedPath(): String = toString().replace('\\', '/')

    private fun String.isInside(root: String): Boolean = this == root || startsWith("$root/")

    private companion object {
        val ROOT_SOURCE_ROOTS = listOf("src/main/kotlin", "src/test/kotlin")
        val MODULE_SOURCE_ROOT_PATTERN = Regex("""^(.+)/src/(main|test)/kotlin/""")
    }
}
