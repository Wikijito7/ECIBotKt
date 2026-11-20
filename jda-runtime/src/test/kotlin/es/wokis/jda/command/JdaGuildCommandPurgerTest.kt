package es.wokis.jda.command

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.events.guild.GuildJoinEvent
import net.dv8tion.jda.api.events.guild.GuildLeaveEvent
import net.dv8tion.jda.api.interactions.commands.Command
import net.dv8tion.jda.api.interactions.commands.build.CommandData
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Test
import java.util.function.Consumer

/**
 * Late-join contract: a guild that joins after startup must not keep a guild-scoped command copy
 * that shadows the global set, so the listener empties that guild once.
 */
class JdaGuildCommandPurgerTest {

    private val purger = JdaGuildCommandPurger()

    @Test
    fun `Given a guild joins When handled Then empty that guild's command set once`() {
        val guildPurge = guildPurge()

        purger.onGuildJoin(joinEvent(guildPurge.guild))

        verify(exactly = 1) { guildPurge.guild.updateCommands() }
        verify(exactly = 1) { guildPurge.action.queue(any(), any()) }
        verify(exactly = 0) { guildPurge.action.addCommands(any<Collection<CommandData>>()) }
    }

    @Test
    fun `Given two different guilds join When handled Then empty each guild command set`() {
        val first = guildPurge()
        val second = guildPurge()

        purger.onGuildJoin(joinEvent(first.guild))
        purger.onGuildJoin(joinEvent(second.guild))

        verify(exactly = 1) { first.action.queue(any(), any()) }
        verify(exactly = 1) { second.action.queue(any(), any()) }
    }

    @Test
    fun `Given the purge PUT is rejected When handled Then log it without throwing`() {
        val guildPurge = guildPurge()
        val failure = IllegalStateException("guild command purge rejected")
        every { guildPurge.action.queue(any(), any()) } answers {
            secondArg<Consumer<Throwable>>().accept(failure)
        }

        assertDoesNotThrow { purger.onGuildJoin(joinEvent(guildPurge.guild)) }

        verify(exactly = 1) { guildPurge.guild.updateCommands() }
        verify(exactly = 1) { guildPurge.action.queue(any(), any()) }
    }

    @Test
    fun `Given an unrelated guild event When handled Then issue no REST call`() {
        val guildPurge = guildPurge()
        val leaveEvent = GuildLeaveEvent(mockk<JDA>(), 0L, guildPurge.guild)

        assertDoesNotThrow { purger.onGuildLeave(leaveEvent) }

        verify(exactly = 0) { guildPurge.guild.updateCommands() }
    }

    private fun joinEvent(guild: Guild): GuildJoinEvent = GuildJoinEvent(mockk<JDA>(), 0L, guild)

    private fun guildPurge(): GuildPurge {
        val guild: Guild = mockk()
        val action: CommandListUpdateAction = mockk()
        every { guild.updateCommands() } returns action
        every { action.queue(any(), any()) } answers {
            firstArg<Consumer<List<Command>?>>().accept(emptyList())
        }
        return GuildPurge(guild = guild, action = action)
    }

    private data class GuildPurge(
        val guild: Guild,
        val action: CommandListUpdateAction,
    )
}
