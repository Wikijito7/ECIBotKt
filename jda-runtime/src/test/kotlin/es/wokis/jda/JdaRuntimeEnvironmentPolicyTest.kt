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
        val sources = productionSources()

        // Command publication is startup-only: the per-guild upsert path is gone, and no listener
        // may register commands while the runtime is live. The guild purger is the single allowed
        // guild-lifecycle listener, and it only empties command sets.
        val upsertOffenders = sources.flatMap { (path, source) ->
            offendersIn(path, source, PER_GUILD_REGISTRATION_TOKENS)
        }
        assertTrue(
            upsertOffenders.isEmpty(),
            "JDA production must not register commands per guild: $upsertOffenders",
        )

        val eventOffenders = sources
            .filterNot { (path, _) -> isGuildPurger(path) }
            .flatMap { (path, source) -> offendersIn(path, source, GUILD_EVENT_TOKENS) }
        assertTrue(
            eventOffenders.isEmpty(),
            "only $GUILD_PURGER_FILE may react to guild lifecycle events: $eventOffenders",
        )

        val purgerSource = requireNotNull(sources.firstOrNull { (path, _) -> isGuildPurger(path) }) {
            "missing the guild command purger source"
        }.second
        assertTrue(
            !purgerSource.contains("upsertCommand"),
            "the guild purger must empty the guild command set, never register into it",
        )
    }

    @Test
    fun `Given the shared loader default When inspected Then JDA uses the same root config path as Kord`() {
        assertEquals(Path.of("./data", "config.json"), DEFAULT_CONFIG_PATH)
    }

    private fun scanProduction(forbiddenTokens: List<String>): List<String> =
        productionSources().flatMap { (path, source) -> offendersIn(path, source, forbiddenTokens) }

    private fun productionSources(): List<Pair<String, String>> {
        val sourceRoot = jdaMainSourceRoot()
        return Files.walk(sourceRoot).use { paths ->
            paths
                .filter { path -> Files.isRegularFile(path) && path.fileName.toString().endsWith(".kt") }
                .map { path -> sourceRoot.relativize(path).toString() to Files.readString(path) }
                .toList()
        }
    }

    private fun offendersIn(relativePath: String, source: String, forbiddenTokens: List<String>): List<String> =
        forbiddenTokens
            .filter { token -> source.contains(token) }
            .map { token -> "$relativePath contains '$token'" }

    private fun isGuildPurger(relativePath: String): Boolean =
        relativePath.substringAfterLast('/').substringAfterLast('\\') == GUILD_PURGER_FILE

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

        private val PER_GUILD_REGISTRATION_TOKENS = listOf(
            "upsertCommand",
        )

        private val GUILD_EVENT_TOKENS = listOf(
            "GuildJoinEvent",
            "onGuildJoin",
            "GuildReadyEvent",
            "onGuildReady",
            "GuildLeaveEvent",
            "onGuildLeave",
            "GuildAvailableEvent",
        )

        private const val GUILD_PURGER_FILE = "JdaGuildCommandPurger.kt"
    }
}
