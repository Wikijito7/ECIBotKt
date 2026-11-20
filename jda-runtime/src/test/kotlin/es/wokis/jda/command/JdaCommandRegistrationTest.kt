package es.wokis.jda.command

import es.wokis.commands.neutral.CommandDefinition
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.interactions.commands.Command
import net.dv8tion.jda.api.interactions.commands.build.CommandData
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.function.Consumer

/**
 * Global-only publication contract: one bulk overwrite for the whole command set, and one empty
 * overwrite per joined guild. The bootstrap calls both; this suite pins each unit separately.
 */
class JdaCommandRegistrationTest {

    @Test
    fun `Given thirteen definitions When publishing globally Then send one bulk overwrite with all of them`() = runTest {
        val jda: JDA = mockk()
        val action: CommandListUpdateAction = mockk()
        val published = mutableListOf<CommandData>()
        every { jda.updateCommands() } returns action
        every { action.queue(any(), any()) } answers {
            firstArg<Consumer<List<Command>?>>().accept(emptyList())
        }
        val definitions = (1..13).map { index ->
            CommandDefinition(name = "command$index", description = "Description $index")
        }
        every { action.addCommands(any<Collection<CommandData>>()) } answers {
            published.addAll(firstArg<Collection<CommandData>>())
            action
        }

        registerGlobalCommands(jda, definitions)

        verify(exactly = 1) { jda.updateCommands() }
        verify(exactly = 1) { action.addCommands(any<Collection<CommandData>>()) }
        verify(exactly = 1) { action.queue(any(), any()) }
        assertEquals(definitions.map { it.name }, published.map { it.name })
    }

    @Test
    fun `Given a definition order When publishing globally Then keep the payload order stable`() = runTest {
        val jda: JDA = mockk()
        val action: CommandListUpdateAction = mockk()
        val published = mutableListOf<CommandData>()
        every { jda.updateCommands() } returns action
        every { action.queue(any(), any()) } answers {
            firstArg<Consumer<List<Command>?>>().accept(emptyList())
        }
        every { action.addCommands(any<Collection<CommandData>>()) } answers {
            published.addAll(firstArg<Collection<CommandData>>())
            action
        }
        val definitions = listOf("locale", "play", "radio").map { name ->
            CommandDefinition(name = name, description = "Description for $name")
        }

        registerGlobalCommands(jda, definitions)

        assertEquals(listOf("locale", "play", "radio"), published.map { it.name })
    }

    @Test
    fun `Given the global PUT emits a REST failure When publishing Then propagate it without a fallback`() = runTest {
        val jda: JDA = mockk()
        val action: CommandListUpdateAction = mockk()
        val expectedFailure = IllegalStateException("global command overwrite rejected")
        every { jda.updateCommands() } returns action
        every { action.addCommands(any<Collection<CommandData>>()) } returns action
        every { action.queue(any(), any()) } answers {
            secondArg<Consumer<Throwable>>().accept(expectedFailure)
        }

        val thrown = runCatching {
            registerGlobalCommands(jda, listOf(CommandDefinition(name = "play", description = "Play a track")))
        }.exceptionOrNull()

        // Coroutine stack-trace recovery copies the exception, so the identity is not asserted here.
        assertTrue(thrown is IllegalStateException, "expected the REST failure, got $thrown")
        assertEquals(expectedFailure.message, thrown?.message)
        verify(exactly = 1) { jda.updateCommands() }
    }

    @Test
    fun `Given two guilds When purging Then empty each guild command set once in input order`() = runTest {
        val events = mutableListOf<String>()
        val first = purgeableGuild("first", events)
        val second = purgeableGuild("second", events)

        purgeGuildCommands(listOf(first.guild, second.guild))

        assertEquals(listOf("first", "second"), events)
        verify(exactly = 1) { first.guild.updateCommands() }
        verify(exactly = 1) { second.guild.updateCommands() }
        verify(exactly = 1) { first.action.queue(any(), any()) }
        verify(exactly = 1) { second.action.queue(any(), any()) }
        verify(exactly = 0) { first.action.addCommands(any<Collection<CommandData>>()) }
        verify(exactly = 0) { second.action.addCommands(any<Collection<CommandData>>()) }
    }

    @Test
    fun `Given no guilds When purging Then issue no REST call and do not fail`() = runTest {
        val outcome = runCatching { purgeGuildCommands(emptyList()) }

        assertTrue(outcome.isSuccess, "an empty guild list must be a no-op, got ${outcome.exceptionOrNull()}")
    }

    @Test
    fun `Given a guild purge fails When purging Then propagate and stop before later guilds`() = runTest {
        val events = mutableListOf<String>()
        val first = purgeableGuild("first", events)
        val expectedFailure = IllegalStateException("guild command purge rejected")
        val failingGuild: Guild = mockk()
        val failingAction: CommandListUpdateAction = mockk()
        every { failingGuild.updateCommands() } returns failingAction
        every { failingAction.queue(any(), any()) } answers {
            secondArg<Consumer<Throwable>>().accept(expectedFailure)
        }
        val untouched: Guild = mockk()

        val thrown = runCatching {
            purgeGuildCommands(listOf(first.guild, failingGuild, untouched))
        }.exceptionOrNull()

        assertTrue(thrown is IllegalStateException, "expected the REST failure, got $thrown")
        assertEquals(expectedFailure.message, thrown?.message)
        assertEquals(listOf("first"), events)
        verify(exactly = 1) { first.guild.updateCommands() }
        verify(exactly = 1) { failingGuild.updateCommands() }
        verify(exactly = 0) { untouched.updateCommands() }
    }

    private fun purgeableGuild(label: String, events: MutableList<String>): PurgeableGuild {
        val guild: Guild = mockk()
        val action: CommandListUpdateAction = mockk()
        every { guild.updateCommands() } returns action
        every { action.queue(any(), any()) } answers {
            events += label
            firstArg<Consumer<List<Command>?>>().accept(emptyList())
        }
        return PurgeableGuild(guild = guild, action = action)
    }

    private data class PurgeableGuild(
        val guild: Guild,
        val action: CommandListUpdateAction,
    )
}
