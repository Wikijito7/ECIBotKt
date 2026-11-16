package es.wokis.jda

import es.wokis.services.config.DEFAULT_CONFIG_PATH
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class JdaRuntimeEnvironmentPolicyTest {

    @Test
    fun `Given the JDA production sources When scanned Then no environment variable is parsed`() {
        val offenders = scanProduction(FORBIDDEN_ENV_TOKENS)

        assertTrue(
            offenders.isEmpty(),
            "JDA production must not read environment configuration: $offenders",
        )
    }

    @Test
    fun `Given the JDA production sources When scanned Then no dev guild config field exists`() {
        val offenders = scanProduction(FORBIDDEN_GUILD_CONFIG_TOKENS)

        assertTrue(
            offenders.isEmpty(),
            "JDA production must not configure a guild id: $offenders",
        )
    }

    @Test
    fun `Given the JDA production sources When scanned Then registration stays a startup-only step`() {
        val offenders = scanProduction(FORBIDDEN_EVENT_REGISTRATION_TOKENS)

        // A guild joined while the runtime is live receives its commands on the next restart. An
        // event-driven registration pass would need its own coverage.
        assertTrue(
            offenders.isEmpty(),
            "JDA production must not register commands from a guild event listener: $offenders",
        )
    }

    @Test
    fun `Given the shared loader default When inspected Then JDA uses the same root config path as Kord`() {
        assertEquals(Path.of("./data", "config.json"), DEFAULT_CONFIG_PATH)
    }

    private fun scanProduction(forbiddenTokens: List<String>): List<String> {
        val sourceRoot = jdaMainSourceRoot()
        return Files.walk(sourceRoot).use { paths ->
            paths
                .filter { path -> Files.isRegularFile(path) && path.fileName.toString().endsWith(".kt") }
                .flatMap { path -> offendersIn(sourceRoot, path, forbiddenTokens).stream() }
                .toList()
        }
    }

    private fun offendersIn(sourceRoot: Path, path: Path, forbiddenTokens: List<String>): List<String> {
        val source = Files.readString(path)
        return forbiddenTokens
            .filter { token -> source.contains(token) }
            .map { token -> "${sourceRoot.relativize(path)} contains '$token'" }
    }

    private fun jdaMainSourceRoot(): Path {
        var directory: Path? = Path.of("").toAbsolutePath().normalize()
        while (directory != null) {
            if (Files.isRegularFile(directory.resolve("settings.gradle.kts"))) {
                val sourceRoot = directory.resolve("jda-runtime/src/main/kotlin")
                assertTrue(Files.isDirectory(sourceRoot), "missing JDA production source root: $sourceRoot")
                return sourceRoot
            }
            directory = directory.parent
        }
        error("cannot locate the repository root from ${Path.of("").toAbsolutePath()}")
    }

    private companion object {
        private val FORBIDDEN_ENV_TOKENS = listOf(
            "System.getenv",
            "getenv(",
            "JDA_CONFIG_PATH",
            "DISCORD_DEV_GUILD_ID",
            "fromEnv(",
        )

        private val FORBIDDEN_GUILD_CONFIG_TOKENS = listOf(
            "discord_dev_guild_id",
            "discordDevGuildId",
            "GUILD_ID_PROPERTY",
        )

        private val FORBIDDEN_EVENT_REGISTRATION_TOKENS = listOf(
            "GuildJoinEvent",
            "onGuildJoin",
            "GuildReadyEvent",
            "onGuildReady",
        )
    }
}
