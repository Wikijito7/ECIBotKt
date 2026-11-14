package es.wokis.jda.player

import es.wokis.domain.model.ChannelId
import es.wokis.player.PlayerMessageId
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.requests.restaction.AuditableRestAction
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction
import net.dv8tion.jda.api.requests.restaction.MessageEditAction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.function.Consumer

class JdaPlayerMessagePortTest {

    private val channelId = ChannelId(42uL)
    private val messageId = PlayerMessageId(900uL)

    @Test
    fun `Given a text channel When sending Then use the exact channel and content and return the message id`() = runTest {
        // Given
        val jda = mockk<JDA>()
        val channel = mockk<TextChannel>()
        val action = mockk<MessageCreateAction>()
        val message = mockk<Message>()
        every { jda.getTextChannelById(42L) } returns channel
        every { channel.sendMessage("Now playing") } returns action
        every { action.queue(any(), any()) } answers {
            firstArg<Consumer<Message>>().accept(message)
        }
        every { message.idLong } returns 900L

        // When
        val result = JdaPlayerMessagePort(jda).send(channelId, "Now playing")

        // Then
        assertEquals(PlayerMessageId(900uL), result)
        verify(exactly = 1) { jda.getTextChannelById(42L) }
        verify(exactly = 1) { channel.sendMessage("Now playing") }
        verify(exactly = 1) { action.queue(any(), any()) }
    }

    @Test
    fun `Given an existing message When editing Then use the exact channel message and content`() = runTest {
        // Given
        val jda = mockk<JDA>()
        val channel = mockk<TextChannel>()
        val action = mockk<MessageEditAction>()
        every { jda.getTextChannelById(42L) } returns channel
        every { channel.editMessageById(900L, "Finished") } returns action
        every { action.queue(any(), any()) } answers {
            firstArg<Consumer<Message?>>().accept(null)
        }

        // When
        JdaPlayerMessagePort(jda).edit(channelId, messageId, "Finished")

        // Then
        verify(exactly = 1) { channel.editMessageById(900L, "Finished") }
        verify(exactly = 1) { action.queue(any(), any()) }
    }

    @Test
    fun `Given an existing message When deleting Then use the exact channel and message`() = runTest {
        // Given
        val jda = mockk<JDA>()
        val channel = mockk<TextChannel>()
        val action = mockk<AuditableRestAction<Void>>()
        every { jda.getTextChannelById(42L) } returns channel
        every { channel.deleteMessageById(900L) } returns action
        every { action.queue(any(), any()) } answers {
            firstArg<Consumer<Void?>>().accept(null)
        }

        // When
        JdaPlayerMessagePort(jda).delete(channelId, messageId)

        // Then
        verify(exactly = 1) { channel.deleteMessageById(900L) }
        verify(exactly = 1) { action.queue(any(), any()) }
    }

    @Test
    fun `Given a REST failure When sending Then propagate the original error`() = runTest {
        // Given
        val jda = mockk<JDA>()
        val channel = mockk<TextChannel>()
        val action = mockk<MessageCreateAction>()
        val expected = IllegalStateException("rest failed")
        every { jda.getTextChannelById(42L) } returns channel
        every { channel.sendMessage("Now playing") } returns action
        every { action.queue(any(), any()) } answers {
            secondArg<Consumer<Throwable>>().accept(expected)
        }

        // When
        val actual = runCatching {
            JdaPlayerMessagePort(jda).send(channelId, "Now playing")
        }.exceptionOrNull()

        // Then
        assertTrue(actual is IllegalStateException)
        assertEquals(expected.message, actual?.message)
    }

    @Test
    fun `Given a REST failure When editing Then propagate the original error`() = runTest {
        // Given
        val jda = mockk<JDA>()
        val channel = mockk<TextChannel>()
        val action = mockk<MessageEditAction>()
        val expected = IllegalArgumentException("edit failed")
        every { jda.getTextChannelById(42L) } returns channel
        every { channel.editMessageById(900L, "Finished") } returns action
        every { action.queue(any(), any()) } answers {
            secondArg<Consumer<Throwable>>().accept(expected)
        }

        // When
        val actual = runCatching {
            JdaPlayerMessagePort(jda).edit(channelId, messageId, "Finished")
        }.exceptionOrNull()

        // Then
        assertTrue(actual is IllegalArgumentException)
        assertEquals(expected.message, actual?.message)
    }

    @Test
    fun `Given a REST failure When deleting Then propagate the original error`() = runTest {
        // Given
        val jda = mockk<JDA>()
        val channel = mockk<TextChannel>()
        val action = mockk<AuditableRestAction<Void>>()
        val expected = IllegalStateException("delete failed")
        every { jda.getTextChannelById(42L) } returns channel
        every { channel.deleteMessageById(900L) } returns action
        every { action.queue(any(), any()) } answers {
            secondArg<Consumer<Throwable>>().accept(expected)
        }

        // When
        val actual = runCatching {
            JdaPlayerMessagePort(jda).delete(channelId, messageId)
        }.exceptionOrNull()

        // Then
        assertTrue(actual is IllegalStateException)
        assertEquals(expected.message, actual?.message)
    }

    @Test
    fun `Given an unknown channel When any operation runs Then fail without another channel fallback`() = runTest {
        // Given
        val jda = mockk<JDA>()
        every { jda.getTextChannelById(any<Long>()) } returns null
        val port = JdaPlayerMessagePort(jda)

        // When
        val send = runCatching { port.send(channelId, "text") }.exceptionOrNull()
        val edit = runCatching { port.edit(channelId, messageId, "text") }.exceptionOrNull()
        val delete = runCatching { port.delete(channelId, messageId) }.exceptionOrNull()

        // Then
        listOf(send, edit, delete).forEach { error ->
            assertTrue(error is IllegalStateException)
            assertTrue(error?.message.orEmpty().contains("not available"))
        }
        verify(exactly = 3) { jda.getTextChannelById(42L) }
    }

    @Test
    fun `Given an id above the signed JDA range When sending Then fail before touching the JDA cache`() = runTest {
        // Given
        val jda = mockk<JDA>()
        val port = JdaPlayerMessagePort(jda)

        // When
        val error = runCatching {
            port.send(ChannelId(ULong.MAX_VALUE), "text")
        }.exceptionOrNull()

        // Then
        assertTrue(error is IllegalArgumentException)
        assertTrue(error?.message.orEmpty().contains("does not fit"))
        verify(exactly = 0) { jda.getTextChannelById(any<Long>()) }
    }

    @Test
    fun `Given an id above the signed JDA range When deleting Then fail before issuing the REST call`() = runTest {
        // Given
        val jda = mockk<JDA>()
        val channel = mockk<TextChannel>()
        every { jda.getTextChannelById(42L) } returns channel
        val port = JdaPlayerMessagePort(jda)

        // When
        val error = runCatching {
            port.delete(channelId, PlayerMessageId(ULong.MAX_VALUE))
        }.exceptionOrNull()

        // Then
        assertTrue(error is IllegalArgumentException)
        assertTrue(error?.message.orEmpty().contains("does not fit"))
        verify(exactly = 0) { channel.deleteMessageById(any<Long>()) }
    }
}
