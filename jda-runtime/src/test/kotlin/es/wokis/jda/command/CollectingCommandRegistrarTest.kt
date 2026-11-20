package es.wokis.jda.command

import es.wokis.commands.neutral.CommandDefinition
import es.wokis.commands.neutral.StringOptionDefinition
import es.wokis.domain.model.GuildId
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The collecting registrar is the boot-time accumulator of the global command set: the guild id is a
 * caller-compatibility placeholder, entries are unique by name, and a real clash fails the boot.
 */
class CollectingCommandRegistrarTest {

    private val registrar = CollectingCommandRegistrar()

    @Test
    fun `Given two commands registered under different guild ids When collected Then both are kept once`() = runTest {
        val first = CommandDefinition(name = "play", description = "Play a track")
        val second = CommandDefinition(name = "skip", description = "Skip the current track")

        registrar.register(GuildId(1uL), first)
        registrar.register(GuildId(2uL), second)

        assertEquals(listOf("play", "skip"), registrar.definitions().map { it.name })
    }

    @Test
    fun `Given the same definition registered under two guild ids When collected Then keep one entry`() = runTest {
        val command = CommandDefinition(name = "queue", description = "Show the queue")

        registrar.register(GuildId(1uL), command)
        registrar.register(GuildId(2uL), command)

        assertEquals(listOf(command), registrar.definitions())
    }

    @Test
    fun `Given two equal definitions built separately When collected Then dedupe without failing`() = runTest {
        // Equality is part of the contract: re-registration that produces an equal definition is
        // not a conflicting definition.
        val first = CommandDefinition(name = "next", description = "Skip to a track", adminOnly = false)
        val second = first.copy()

        registrar.register(GuildId(1uL), first)
        registrar.register(GuildId(2uL), second)

        assertEquals(1, registrar.definitions().size)
        assertSame(first, registrar.definitions().single())
    }

    @Test
    fun `Given two different definitions sharing a name When collected Then fail loudly`() = runTest {
        registrar.register(GuildId(1uL), CommandDefinition(name = "sound", description = "Play a sound"))

        val failure = runCatching {
            registrar.register(GuildId(2uL), CommandDefinition(name = "sound", description = "Play another sound"))
        }.exceptionOrNull()

        assertTrue(failure is IllegalStateException, "expected the production check() failure, got $failure")
        assertEquals("Conflicting definitions collected for /sound", failure?.message)
    }

    @Test
    fun `Given a conflicting definition When collected Then the first definition stays collected`() = runTest {
        val first = CommandDefinition(
            name = "locale",
            description = "Set the locale",
            stringOption = StringOptionDefinition(name = "locale", description = "Locale"),
        )
        val conflicting = first.copy(adminOnly = true)

        registrar.register(GuildId(1uL), first)
        runCatching { registrar.register(GuildId(2uL), conflicting) }

        assertEquals(listOf(first), registrar.definitions())
    }

    @Test
    fun `Given commands registered in a specific order When definitions is read Then registration order is preserved`() =
        runTest {
            val names = listOf("locale", "play", "player", "skip", "radio")
            names.forEachIndexed { index, name ->
                registrar.register(
                    GuildId(index.toULong()),
                    CommandDefinition(name = name, description = "Description for $name"),
                )
            }

            assertEquals(names, registrar.definitions().map { it.name })
            assertEquals(
                names,
                registrar.definitions().map { it.name },
                "definitions() must stay stable across reads so the bulk overwrite payload is deterministic",
            )
        }
}
