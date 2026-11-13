package es.wokis.jda.command

import es.wokis.commands.neutral.CommandDefinition
import es.wokis.domain.model.GuildId
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.exceptions.ErrorResponseException
import net.dv8tion.jda.api.interactions.commands.Command
import net.dv8tion.jda.api.interactions.commands.build.CommandData
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import net.dv8tion.jda.api.requests.ErrorResponse
import net.dv8tion.jda.api.requests.Response
import net.dv8tion.jda.api.requests.RestAction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.function.Consumer

class JdaGuildCommandRegistrarTest {

    private val jda: JDA = mockk()
    private val guild: Guild = mockk()

    @Test
    fun `Given a member guild When registering Then publish the command only in that guild`() = runTest {
        // Given the neutral guild id resolves to this exact JDA guild
        val guildId = GuildId(1234567890123456789uL)
        val jdaGuildId = 1234567890123456789L
        val command = CommandDefinition(
            name = "walkthrough",
            description = "Start the guided server walkthrough",
        )
        val published = slot<SlashCommandData>()
        val upsertAction = successfulUpsertAction()
        every { jda.getGuildById(jdaGuildId) } returns guild
        every { guild.upsertCommand(capture(published)) } returns upsertAction

        // When
        JdaGuildCommandRegistrar(jda).register(guildId, command)

        // Then the command is sent through that guild's upsert route...
        verify(exactly = 1) { jda.getGuildById(jdaGuildId) }
        verify(exactly = 1) { guild.upsertCommand(any<CommandData>()) }
        verify(exactly = 1) { upsertAction.queue(any(), any()) }

        // ...with the neutral definition mapped onto the slash command payload
        assertEquals(command.name, published.captured.name)
        assertEquals(command.description, published.captured.description)

        // ...and never through JDA's application-wide registration route
        verify(exactly = 0) { jda.upsertCommand(any<CommandData>()) }
        verify(exactly = 0) { jda.upsertCommand(any<String>(), any<String>()) }
    }

    @Test
    fun `Given the guild upsert emits a REST failure When registering Then propagate it without an application-wide fallback`() = runTest {
        // Given an upsert whose failure callback fires with JDA's REST error
        val guildId = GuildId(1234567890123456789uL)
        val jdaGuildId = 1234567890123456789L
        val command = CommandDefinition(
            name = "walkthrough",
            description = "Start the guided server walkthrough",
        )
        val expectedFailure = ErrorResponseException.create(
            ErrorResponse.MISSING_ACCESS,
            Response(
                IllegalStateException("Discord rejected the guild command upsert"),
                emptySet(),
            ),
        )
        val upsertAction: RestAction<Command> = mockk()
        every { jda.getGuildById(jdaGuildId) } returns guild
        every { guild.upsertCommand(any<CommandData>()) } returns upsertAction
        every { upsertAction.queue(any(), any()) } answers {
            secondArg<Consumer<Throwable>>().accept(expectedFailure)
        }

        // When
        val failure = runCatching {
            JdaGuildCommandRegistrar(jda).register(guildId, command)
        }.exceptionOrNull()

        // Then the guild-scoped REST failure reaches the caller unchanged...
        assertSame(expectedFailure, failure)
        verify(exactly = 1) { jda.getGuildById(jdaGuildId) }
        verify(exactly = 1) { guild.upsertCommand(any<CommandData>()) }
        verify(exactly = 1) { upsertAction.queue(any(), any()) }

        // ...and no application-wide registration fallback is attempted
        verify(exactly = 0) { jda.upsertCommand(any<CommandData>()) }
        verify(exactly = 0) { jda.upsertCommand(any<String>(), any<String>()) }
    }

    @Test
    fun `Given a guild the bot is not in When registering Then fail without a global fallback`() = runTest {
        // Given the requested guild cannot be resolved
        val guildId = GuildId(987654321uL)
        val jdaGuildId = 987654321L
        val command = CommandDefinition(
            name = "walkthrough",
            description = "Start the guided server walkthrough",
        )
        every { jda.getGuildById(jdaGuildId) } returns null

        // When
        val failure = runCatching {
            JdaGuildCommandRegistrar(jda).register(guildId, command)
        }.exceptionOrNull()

        // Then the registration fails fast instead of publishing anywhere else
        assertTrue(failure is IllegalStateException)
        assertEquals(
            "The bot is not a member of guild $guildId, so /${command.name} cannot be registered",
            failure?.message,
        )
        verify(exactly = 0) { guild.upsertCommand(any<CommandData>()) }
        verify(exactly = 0) { jda.upsertCommand(any<CommandData>()) }
        verify(exactly = 0) { jda.upsertCommand(any<String>(), any<String>()) }
    }

    @Test
    fun `Given a guild id outside JDA's signed range When registering Then reject before touching JDA`() = runTest {
        // Given an id that cannot be represented by JDA's signed snowflake
        val outOfRangeGuildId = GuildId(Long.MAX_VALUE.toULong() + 1uL)
        val command = CommandDefinition(
            name = "walkthrough",
            description = "Start the guided server walkthrough",
        )

        // When
        val failure = runCatching {
            JdaGuildCommandRegistrar(jda).register(outOfRangeGuildId, command)
        }.exceptionOrNull()

        // Then the conversion boundary rejects it before any gateway lookup or registration
        assertTrue(failure is IllegalArgumentException)
        verify(exactly = 0) { jda.getGuildById(any<Long>()) }
        verify(exactly = 0) { jda.getGuildById(any<String>()) }
        verify(exactly = 0) { jda.upsertCommand(any<CommandData>()) }
    }

    private fun successfulUpsertAction(): RestAction<Command> {
        val action: RestAction<Command> = mockk()
        every { action.queue(any(), any()) } answers {
            firstArg<Consumer<Command?>>().accept(null)
        }
        return action
    }
}
