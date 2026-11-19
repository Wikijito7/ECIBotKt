package es.wokis.jda.player

import es.wokis.domain.model.ChannelId
import es.wokis.message.EmbedView
import es.wokis.message.MessageView
import es.wokis.player.PlayerMessageId
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.MessageHistory
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.requests.RestAction
import net.dv8tion.jda.api.requests.restaction.AuditableRestAction
import net.dv8tion.jda.api.requests.restaction.ChannelAction
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction
import net.dv8tion.jda.api.utils.messages.MessageCreateData
import org.junit.jupiter.api.Test
import java.util.EnumSet
import java.util.function.Consumer
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JdaPlayerChannelServiceTest {

    @Test
    fun `Given an existing player channel in any case When setup runs Then reuse it without creating`() = runTest {
        val existing = ChannelFixture(name = "PLAYER")
        existing.historyPages(emptyList())
        val guild = mockk<Guild> {
            every { textChannels } returns listOf(existing.channel)
            every { name } returns "Jungle"
        }

        val result = JdaPlayerChannelService().setUp(guild, playerView())

        assertEquals(ChannelId(42uL), result?.channelId)
        assertEquals(PlayerMessageId(900uL), result?.messageId)
        assertFalse(requireNotNull(result).isNewChannel)
        verify(exactly = 0) { guild.createTextChannel("player") }
    }

    @Test
    fun `Given no player channel When setup runs Then create it with the exact everyone overwrite`() = runTest {
        val existing = ChannelFixture(name = "general")
        existing.historyPages(emptyList())
        val created = ChannelFixture(id = 77L, name = "player")
        created.historyPages(emptyList())
        val role = mockk<Role>()
        val createAction = mockk<ChannelAction<TextChannel>>()
        every {
            createAction.addPermissionOverride(role, any<Collection<Permission>>(), any<Collection<Permission>>())
        } returns createAction
        every { createAction.queue(any(), any()) } answers {
            firstArg<Consumer<TextChannel>>().accept(created.channel)
        }
        val guild = mockk<Guild> {
            every { textChannels } returns listOf(existing.channel)
            every { publicRole } returns role
            every { name } returns "Jungle"
            every { createTextChannel("player") } returns createAction
        }

        val result = JdaPlayerChannelService().setUp(guild, playerView())

        assertTrue(requireNotNull(result).isNewChannel)
        assertEquals(ChannelId(77uL), result.channelId)
        verify(exactly = 1) {
            createAction.addPermissionOverride(
                role,
                EnumSet.of(Permission.VIEW_CHANNEL, Permission.MESSAGE_HISTORY),
                EnumSet.of(Permission.MESSAGE_SEND),
            )
        }
    }

    @Test
    fun `Given a hundred backlog messages When cleared Then delete them in ninety plus ten chunks`() = runTest {
        val channel = ChannelFixture()
        val messages = (1..100).map { message("$it") }
        channel.historyPages(messages, emptyList())
        val chunks = mutableListOf<Collection<String>>()
        val deleteAction = successfulDeleteAction()
        every { channel.channel.deleteMessagesByIds(capture(chunks)) } returns deleteAction

        JdaPlayerChannelService().setUp(guildWith(channel), playerView())

        assertEquals(listOf(90, 10), chunks.map(Collection<String>::size))
        assertEquals((1..90).map(Int::toString), chunks.first().toList())
        assertEquals((91..100).map(Int::toString), chunks.last().toList())
        verify(exactly = 0) { channel.channel.deleteMessageById(any<String>()) }
    }

    @Test
    fun `Given a failing bulk delete When clearing Then fall back to one delete per message`() = runTest {
        val channel = ChannelFixture()
        val messages = listOf(message("10"), message("11"))
        channel.historyPages(messages)
        val bulkAction = mockk<RestAction<Void>> {
            every { queue(any(), any()) } answers {
                secondArg<Consumer<Throwable>>().accept(IllegalStateException("bulk delete failed"))
            }
        }
        val deletedIds = mutableListOf<String>()
        every { channel.channel.deleteMessagesByIds(any()) } returns bulkAction
        every { channel.channel.deleteMessageById(capture(deletedIds)) } returns successfulDeleteAction()

        val result = JdaPlayerChannelService().setUp(guildWith(channel), playerView())

        assertEquals(listOf("10", "11"), deletedIds)
        assertTrue(requireNotNull(result).messageId.value > 0uL)
    }

    @Test
    fun `Given a backlog ending in a single leftover When cleared Then bulk delete ninety and delete the last alone`() =
        runTest {
            val channel = ChannelFixture()
            channel.historyPages((1..91).map { message("$it") })
            val bulkChunks = mutableListOf<Collection<String>>()
            every { channel.channel.deleteMessagesByIds(capture(bulkChunks)) } returns successfulDeleteAction()
            val deletedIds = mutableListOf<String>()
            every { channel.channel.deleteMessageById(capture(deletedIds)) } returns successfulDeleteAction()

            JdaPlayerChannelService().setUp(guildWith(channel), playerView())

            assertEquals(
                listOf(90),
                bulkChunks.map(Collection<String>::size),
                "the leftover must skip the bulk endpoint",
            )
            assertEquals((1..90).map(Int::toString), bulkChunks.single().toList())
            assertEquals(listOf("91"), deletedIds)
            verify(exactly = 1) { channel.channel.deleteMessagesByIds(any()) }
        }

    @Test
    fun `Given a one message backlog When cleared Then delete it per message without a bulk attempt`() = runTest {
        val channel = ChannelFixture()
        channel.historyPages(listOf(message("7")))
        val deletedIds = mutableListOf<String>()
        every { channel.channel.deleteMessageById(capture(deletedIds)) } returns successfulDeleteAction()

        val result = JdaPlayerChannelService().setUp(guildWith(channel), playerView())

        assertEquals(listOf("7"), deletedIds, "a single message must be deleted individually")
        verify(exactly = 1) { channel.channel.deleteMessageById(any<String>()) }
        verify(exactly = 0) { channel.channel.deleteMessagesByIds(any()) }
        assertTrue(requireNotNull(result).messageId.value > 0uL)
    }

    @Test
    fun `Given a channel creation failure When setup runs Then return null without crashing`() = runTest {
        val guild = mockk<Guild> {
            every { textChannels } returns emptyList()
            every { name } returns "Jungle"
            every { createTextChannel("player") } throws IllegalStateException("missing permissions")
        }

        val result = JdaPlayerChannelService().setUp(guild, playerView())

        assertNull(result)
    }

    @Test
    fun `Given a message send failure When setup runs Then return null without crashing`() = runTest {
        val channel = ChannelFixture()
        channel.historyPages(emptyList())
        channel.failSend(IllegalStateException("missing send permission"))

        val result = JdaPlayerChannelService().setUp(guildWith(channel), playerView())

        assertNull(result)
    }

    @Test
    fun `Given a backlog larger than five pages When cleared Then keep clearing until a short page`() = runTest {
        val channel = ChannelFixture()
        val fullPages = (0 until 6).map { page ->
            val first = page * 100 + 1
            (first until first + 100).map { message("$it") }
        }
        val shortPage = (601..650).map { message("$it") }
        channel.historyPages(*(fullPages + listOf(shortPage)).toTypedArray())
        val chunks = mutableListOf<Collection<String>>()
        every { channel.channel.deleteMessagesByIds(capture(chunks)) } returns successfulDeleteAction()

        JdaPlayerChannelService().setUp(guildWith(channel), playerView())

        assertEquals(
            listOf(90, 10, 90, 10, 90, 10, 90, 10, 90, 10, 90, 10, 50),
            chunks.map(Collection<String>::size),
            "every page must be cleared in ninety-message chunks until the short page",
        )
        assertEquals((1..650).map(Int::toString), chunks.flatten())
        verify(exactly = 13) { channel.channel.deleteMessagesByIds(any()) }
        verify(exactly = 0) { channel.channel.deleteMessageById(any<String>()) }
    }

    @Test
    fun `Given a bulk delete that fails on one chunk When a multi page backlog is cleared Then fall back per message`() =
        runTest {
            val channel = ChannelFixture()
            channel.historyPages(
                (1..100).map { message("$it") },
                (101..200).map { message("$it") },
                (201..252).map { message("$it") },
            )
            val handledChunks = mutableListOf<Collection<String>>()
            val failingAction = mockk<RestAction<Void>> {
                every { queue(any(), any()) } answers {
                    secondArg<Consumer<Throwable>>().accept(IllegalStateException("bulk delete failed"))
                }
            }
            every { channel.channel.deleteMessagesByIds(capture(handledChunks)) } answers {
                if (handledChunks.size == 2) failingAction else successfulDeleteAction()
            }
            val deletedIds = mutableListOf<String>()
            every { channel.channel.deleteMessageById(capture(deletedIds)) } returns successfulDeleteAction()

            val result = JdaPlayerChannelService().setUp(guildWith(channel), playerView())

            assertEquals((91..100).map(Int::toString), deletedIds, "only the failed chunk falls back per message")
            assertEquals(listOf(90, 10, 90, 10, 52), handledChunks.map(Collection<String>::size))
            assertTrue(requireNotNull(result).messageId.value > 0uL)
        }

    private fun guildWith(channel: ChannelFixture): Guild = mockk {
        every { textChannels } returns listOf(channel.channel)
        every { name } returns "Jungle"
    }

    private fun playerView(): MessageView = MessageView(embed = EmbedView(title = "Player"))

    private fun message(messageId: String): Message = mockk {
        every { id } returns messageId
    }

    private fun successfulDeleteAction(): AuditableRestAction<Void> = mockk {
        every { queue(any(), any()) } answers { firstArg<Consumer<Void?>>().accept(null) }
    }

    private class ChannelFixture(
        private val id: Long = 42L,
        private val name: String = "player",
    ) {
        private val history: MessageHistory = mockk()
        private val sendAction: MessageCreateAction = mockk()
        private val sentMessage: Message = mockk {
            every { idLong } returns 900L
        }

        val channel: TextChannel = mockk {
            every { idLong } returns this@ChannelFixture.id
            every { getName() } returns this@ChannelFixture.name
            every { history } returns this@ChannelFixture.history
            every { sendMessage(any<MessageCreateData>()) } returns sendAction
        }

        init {
            every { sendAction.queue(any(), any()) } answers {
                firstArg<Consumer<Message>>().accept(sentMessage)
            }
        }

        fun historyPages(vararg pages: List<Message>) {
            val actions = pages.map { page ->
                mockk<RestAction<List<Message>>> {
                    every { queue(any(), any()) } answers {
                        firstArg<Consumer<List<Message>>>().accept(page)
                    }
                }
            }
            every { history.retrievePast(100) } returnsMany actions.toList()
        }

        fun failSend(error: Throwable) {
            every { sendAction.queue(any(), any()) } answers {
                secondArg<Consumer<Throwable>>().accept(error)
            }
        }
    }
}
