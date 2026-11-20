package es.wokis.jda.player

import es.wokis.domain.model.ChannelId
import es.wokis.message.MessageView
import es.wokis.player.PlayerMessageId
import es.wokis.player.PlayerMessagePermanentFailure
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.channel.ChannelType
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.exceptions.ErrorResponseException
import net.dv8tion.jda.api.exceptions.MissingAccessException
import net.dv8tion.jda.api.exceptions.ObfuscatedChannelException
import net.dv8tion.jda.api.exceptions.RateLimitedException
import net.dv8tion.jda.api.requests.ErrorResponse
import net.dv8tion.jda.api.requests.Route
import net.dv8tion.jda.api.requests.restaction.AuditableRestAction
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction
import net.dv8tion.jda.api.requests.restaction.MessageEditAction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeoutException
import java.util.function.Consumer

class JdaPlayerMessagePortTest {

    private val channelId = ChannelId(42uL)
    private val messageId = PlayerMessageId(900uL)

    @Test
    fun `Given a text channel When sending Then use the exact channel and content and return the message id`() = runTest {
        val jda = mockk<JDA>()
        val channel = textChannel()
        val action = mockk<MessageCreateAction>()
        val message = mockk<Message>()
        every { jda.getTextChannelById(42L) } returns channel
        every { channel.sendMessage("Now playing") } returns action
        every { action.queue(any(), any()) } answers {
            firstArg<Consumer<Message>>().accept(message)
        }
        every { message.idLong } returns 900L

        val result = JdaPlayerMessagePort(jda).send(channelId, "Now playing")

        assertEquals(PlayerMessageId(900uL), result)
        verify(exactly = 1) { jda.getTextChannelById(42L) }
        verify(exactly = 1) { channel.sendMessage("Now playing") }
        verify(exactly = 1) { action.queue(any(), any()) }
    }

    @Test
    fun `Given an existing message When editing Then use the exact channel message and content`() = runTest {
        val jda = mockk<JDA>()
        val channel = textChannel()
        val action = mockk<MessageEditAction>()
        every { jda.getTextChannelById(42L) } returns channel
        every { channel.editMessageById(900L, "Finished") } returns action
        every { action.queue(any(), any()) } answers {
            firstArg<Consumer<Message?>>().accept(null)
        }

        JdaPlayerMessagePort(jda).edit(channelId, messageId, "Finished")

        verify(exactly = 1) { channel.editMessageById(900L, "Finished") }
        verify(exactly = 1) { action.queue(any(), any()) }
    }

    @Test
    fun `Given a text-only view When content is null or blank Then fail without REST and non-blank text still round-trips`() =
        runTest {
            val jda = mockk<JDA>()
            val channel = textChannel()
            val createAction = mockk<MessageCreateAction>()
            val editAction = mockk<MessageEditAction>()
            val message = mockk<Message>()
            every { jda.getTextChannelById(42L) } returns channel
            every { channel.sendMessage("Live") } returns createAction
            every { channel.editMessageById(900L, "Live") } returns editAction
            every { createAction.queue(any(), any()) } answers {
                firstArg<Consumer<Message>>().accept(message)
            }
            every { editAction.queue(any(), any()) } answers {
                firstArg<Consumer<Message?>>().accept(null)
            }
            every { message.idLong } returns 900L
            val port = JdaPlayerMessagePort(jda)
            val emptyViews = listOf(MessageView(), MessageView(content = ""), MessageView(content = "   "))

            val sendErrors = emptyViews.map { view ->
                runCatching { port.send(channelId, view) }.exceptionOrNull()
            }
            val editErrors = emptyViews.map { view ->
                runCatching { port.edit(channelId, messageId, view) }.exceptionOrNull()
            }

            // Then: every empty text-only view is rejected before JDA is asked to send or edit anything.
            (sendErrors + editErrors).forEach { error ->
                assertTrue(error is IllegalArgumentException)
                assertEquals("A text-only player message requires non-blank content", error?.message)
            }
            verify(exactly = 0) { channel.sendMessage(any<String>()) }
            verify(exactly = 0) { channel.editMessageById(any<Long>(), any<String>()) }

            val sent = port.send(channelId, MessageView(content = "Live"))
            port.edit(channelId, messageId, MessageView(content = "Live"))

            assertEquals(PlayerMessageId(900uL), sent)
            verify(exactly = 1) { channel.sendMessage("Live") }
            verify(exactly = 1) { channel.editMessageById(900L, "Live") }
        }

    @Test
    fun `Given an existing message When deleting Then use the exact channel and message`() = runTest {
        val jda = mockk<JDA>()
        val channel = textChannel()
        val action = mockk<AuditableRestAction<Void>>()
        every { jda.getTextChannelById(42L) } returns channel
        every { channel.deleteMessageById(900L) } returns action
        every { action.queue(any(), any()) } answers {
            firstArg<Consumer<Void?>>().accept(null)
        }

        JdaPlayerMessagePort(jda).delete(channelId, messageId)

        verify(exactly = 1) { channel.deleteMessageById(900L) }
        verify(exactly = 1) { action.queue(any(), any()) }
    }

    @Test
    fun `Given a REST failure When sending Then propagate the original error`() = runTest {
        val jda = mockk<JDA>()
        val channel = textChannel()
        val action = mockk<MessageCreateAction>()
        val expected = IllegalStateException("rest failed")
        every { jda.getTextChannelById(42L) } returns channel
        every { channel.sendMessage("Now playing") } returns action
        every { action.queue(any(), any()) } answers {
            secondArg<Consumer<Throwable>>().accept(expected)
        }

        val actual = runCatching {
            JdaPlayerMessagePort(jda).send(channelId, "Now playing")
        }.exceptionOrNull()

        assertTrue(actual is IllegalStateException)
        assertEquals(expected.message, actual?.message)
    }

    @Test
    fun `Given a REST failure When editing Then propagate the original error`() = runTest {
        val jda = mockk<JDA>()
        val channel = textChannel()
        val action = mockk<MessageEditAction>()
        val expected = IllegalArgumentException("edit failed")
        every { jda.getTextChannelById(42L) } returns channel
        every { channel.editMessageById(900L, "Finished") } returns action
        every { action.queue(any(), any()) } answers {
            secondArg<Consumer<Throwable>>().accept(expected)
        }

        val actual = runCatching {
            JdaPlayerMessagePort(jda).edit(channelId, messageId, "Finished")
        }.exceptionOrNull()

        assertTrue(actual is IllegalArgumentException)
        assertEquals(expected.message, actual?.message)
    }

    @Test
    fun `Given an unknown message response When editing Then report a permanent failure with the original cause`() = runTest {
        val jda = mockk<JDA>()
        val channel = textChannel()
        val action = mockk<MessageEditAction>()
        val cause = mockk<ErrorResponseException>(relaxed = true) {
            every { errorResponse } returns ErrorResponse.UNKNOWN_MESSAGE
        }
        every { jda.getTextChannelById(42L) } returns channel
        every { channel.editMessageById(900L, "Finished") } returns action
        every { action.queue(any(), any()) } answers {
            secondArg<Consumer<Throwable>>().accept(cause)
        }

        val actual = runCatching {
            JdaPlayerMessagePort(jda).edit(channelId, messageId, "Finished")
        }.exceptionOrNull()

        assertTrue(actual is PlayerMessagePermanentFailure)
        assertSame(cause, actual?.cause)
    }

    @Test
    fun `Given a real missing access failure When editing Then report a permanent failure`() = runTest {
        val jda = mockk<JDA>()
        val channel = textChannel()
        val action = mockk<MessageEditAction>()
        val cause = MissingAccessException(channel, Permission.MESSAGE_SEND)
        every { jda.getTextChannelById(42L) } returns channel
        every { channel.editMessageById(900L, "Finished") } returns action
        every { action.queue(any(), any()) } answers {
            secondArg<Consumer<Throwable>>().accept(cause)
        }

        val actual = runCatching {
            JdaPlayerMessagePort(jda).edit(channelId, messageId, "Finished")
        }.exceptionOrNull()

        assertTrue(actual is PlayerMessagePermanentFailure)
        assertSame(cause, actual?.cause)
    }

    @Test
    fun `Given a missing permissions response When editing Then report a permanent failure`() = runTest {
        val jda = mockk<JDA>()
        val channel = textChannel()
        val action = mockk<MessageEditAction>()
        val cause = mockk<ErrorResponseException>(relaxed = true) {
            every { errorResponse } returns ErrorResponse.MISSING_PERMISSIONS
        }
        every { jda.getTextChannelById(42L) } returns channel
        every { channel.editMessageById(900L, "Finished") } returns action
        every { action.queue(any(), any()) } answers {
            secondArg<Consumer<Throwable>>().accept(cause)
        }

        val actual = runCatching {
            JdaPlayerMessagePort(jda).edit(channelId, messageId, "Finished")
        }.exceptionOrNull()

        assertTrue(actual is PlayerMessagePermanentFailure)
    }

    @Test
    fun `Given a timed out edit When editing Then propagate the original retryable error`() = runTest {
        val jda = mockk<JDA>()
        val channel = textChannel()
        val action = mockk<MessageEditAction>()
        val cause = TimeoutException("read timed out")
        every { jda.getTextChannelById(42L) } returns channel
        every { channel.editMessageById(900L, "Finished") } returns action
        every { action.queue(any(), any()) } answers {
            secondArg<Consumer<Throwable>>().accept(cause)
        }

        val actual = runCatching {
            JdaPlayerMessagePort(jda).edit(channelId, messageId, "Finished")
        }.exceptionOrNull()

        assertTrue(actual is TimeoutException, "a timeout must keep its original type so the caller retries")
        assertEquals(cause.message, actual?.message)
        assertFalse(actual is PlayerMessagePermanentFailure)
    }

    @Test
    fun `Given a real rate limited edit When editing Then propagate the RateLimitedException unwrapped`() = runTest {
        val jda = mockk<JDA>()
        val channel = textChannel()
        val action = mockk<MessageEditAction>()
        val route = Route.patch("channels/{channel_id}/messages/{message_id}")
            .compile(CHANNEL_ID.toString(), MESSAGE_ID.toString())
        val cause = RateLimitedException(route, RATE_LIMIT_RETRY_AFTER_MILLIS)
        every { jda.getTextChannelById(42L) } returns channel
        every { channel.editMessageById(900L, "Finished") } returns action
        every { action.queue(any(), any()) } answers {
            secondArg<Consumer<Throwable>>().accept(cause)
        }

        val actual = runCatching {
            JdaPlayerMessagePort(jda).edit(channelId, messageId, "Finished")
        }.exceptionOrNull()

        // Then: the real JDA rate limit keeps its type and its retry-after so the caller backs off,
        // and the permanent wrapper never re-wraps it because the same instance escapes.
        assertTrue(actual is RateLimitedException, "a rate limit must keep its original type so the caller retries")
        val rateLimited = actual as RateLimitedException
        assertSame(cause, rateLimited)
        assertEquals(RATE_LIMIT_RETRY_AFTER_MILLIS, rateLimited.retryAfter)
    }

    @Test
    fun `Given a cancellation While editing Then rethrow the cancellation unchanged`() = runTest {
        val jda = mockk<JDA>()
        val channel = textChannel()
        val action = mockk<MessageEditAction>()
        val cause = CancellationException("coordinator cancelled")
        every { jda.getTextChannelById(42L) } returns channel
        every { channel.editMessageById(900L, "Finished") } returns action
        every { action.queue(any(), any()) } answers {
            secondArg<Consumer<Throwable>>().accept(cause)
        }

        val actual = runCatching {
            JdaPlayerMessagePort(jda).edit(channelId, messageId, "Finished")
        }.exceptionOrNull()

        assertTrue(
            actual is CancellationException,
            "a cancellation must escape as itself, never as a permanent failure",
        )
        assertEquals(cause.message, actual?.message)
        assertFalse(actual is PlayerMessagePermanentFailure)
    }

    @Test
    fun `Given a REST failure When deleting Then propagate the original error`() = runTest {
        val jda = mockk<JDA>()
        val channel = textChannel()
        val action = mockk<AuditableRestAction<Void>>()
        val expected = IllegalStateException("delete failed")
        every { jda.getTextChannelById(42L) } returns channel
        every { channel.deleteMessageById(900L) } returns action
        every { action.queue(any(), any()) } answers {
            secondArg<Consumer<Throwable>>().accept(expected)
        }

        val actual = runCatching {
            JdaPlayerMessagePort(jda).delete(channelId, messageId)
        }.exceptionOrNull()

        assertTrue(actual is IllegalStateException)
        assertEquals(expected.message, actual?.message)
    }

    @Test
    fun `Given an unknown channel When any operation runs Then fail without another channel fallback`() = runTest {
        val jda = mockk<JDA>()
        every { jda.getTextChannelById(any<Long>()) } returns null
        val port = JdaPlayerMessagePort(jda)

        val send = runCatching { port.send(channelId, "text") }.exceptionOrNull()
        val edit = runCatching { port.edit(channelId, messageId, "text") }.exceptionOrNull()
        val delete = runCatching { port.delete(channelId, messageId) }.exceptionOrNull()

        // Then: send and delete keep the retryable asymmetry; only an edit reports the permanent loss.
        listOf(send, delete).forEach { error ->
            assertTrue(error is IllegalStateException)
            assertFalse(error is PlayerMessagePermanentFailure)
            assertEquals(MISSING_CHANNEL_MESSAGE, error?.message)
        }
        assertTrue(edit is PlayerMessagePermanentFailure)
        assertEquals(MISSING_CHANNEL_MESSAGE, edit?.message)
        assertNull(edit?.cause, "an unresolvable channel has no gateway cause to preserve")
        verify(exactly = 3) { jda.getTextChannelById(42L) }
    }

    @Test
    fun `Given a deleted player channel When editing Then report a permanent failure the coordinator can drop`() = runTest {
        val jda = mockk<JDA>()
        every { jda.getTextChannelById(42L) } returns null
        val port = JdaPlayerMessagePort(jda)

        val actual = runCatching {
            port.edit(channelId, messageId, "Finished")
        }.exceptionOrNull()

        // Then: the type is the coordinator-facing contract that makes /player rebuild the handle.
        assertTrue(actual is PlayerMessagePermanentFailure)
        assertEquals(MISSING_CHANNEL_MESSAGE, actual?.message)
        assertNull(actual?.cause)
        assertFalse(
            actual?.message.orEmpty().contains(CHANNEL_ID.toString()),
            "the permanent failure must stay value-free",
        )
        verify(exactly = 1) { jda.getTextChannelById(any<Long>()) }
    }

    @Test
    fun `Given an obfuscated channel When any operation runs Then fail fast before any REST call`() = runTest {
        val jda = mockk<JDA>()
        val channel = mockk<TextChannel> {
            every { isObfuscated } returns true
            every { guild } returns mockk { every { idLong } returns GUILD_ID }
            every { idLong } returns CHANNEL_ID
            every { type } returns ChannelType.TEXT
        }
        every { jda.getTextChannelById(42L) } returns channel
        val port = JdaPlayerMessagePort(jda)

        val send = runCatching { port.send(channelId, "text") }.exceptionOrNull()
        val edit = runCatching { port.edit(channelId, messageId, "text") }.exceptionOrNull()
        val delete = runCatching { port.delete(channelId, messageId) }.exceptionOrNull()

        // Then: send and delete report the hidden channel; an edit reports a permanent loss so the
        // coordinator may drop the handle. No operation ever echoes the channel id.
        listOf(send, delete).forEach { error ->
            assertTrue(error is ObfuscatedChannelException)
            assertFalse(error?.message.orEmpty().contains(CHANNEL_ID.toString()))
        }
        assertTrue(edit is PlayerMessagePermanentFailure)
        assertTrue(edit?.cause is ObfuscatedChannelException)
        assertFalse(edit?.message.orEmpty().contains(CHANNEL_ID.toString()))
        verify(exactly = 3) { jda.getTextChannelById(42L) }
        verify(exactly = 0) { channel.sendMessage(any<String>()) }
        verify(exactly = 0) { channel.editMessageById(any<Long>(), any<String>()) }
        verify(exactly = 0) { channel.deleteMessageById(any<Long>()) }
    }

    @Test
    fun `Given an id above the signed JDA range When sending Then fail before touching the JDA cache`() = runTest {
        val jda = mockk<JDA>()
        val port = JdaPlayerMessagePort(jda)
        val oversizedChannelId = ChannelId(ULong.MAX_VALUE)

        val error = runCatching {
            port.send(oversizedChannelId, "text")
        }.exceptionOrNull()

        // Then: the guard stays, and the message is generic instead of echoing the snowflake.
        assertTrue(error is IllegalArgumentException)
        assertEquals("Channel id does not fit in JDA's signed 64-bit id range", error?.message)
        assertFalse(error?.message.orEmpty().contains(oversizedChannelId.value.toString()))
        verify(exactly = 0) { jda.getTextChannelById(any<Long>()) }
    }

    @Test
    fun `Given an id above the signed JDA range When editing Then fail before issuing the REST call`() = runTest {
        val jda = mockk<JDA>()
        val channel = textChannel()
        every { jda.getTextChannelById(42L) } returns channel
        val port = JdaPlayerMessagePort(jda)
        val oversizedMessageId = PlayerMessageId(ULong.MAX_VALUE)

        val error = runCatching {
            port.edit(channelId, oversizedMessageId, "text")
        }.exceptionOrNull()

        // Then: the guard stays, and the message is generic instead of echoing the snowflake.
        assertTrue(error is IllegalArgumentException)
        assertEquals("Message id does not fit in JDA's signed 64-bit id range", error?.message)
        assertFalse(error?.message.orEmpty().contains(oversizedMessageId.value.toString()))
        verify(exactly = 0) { channel.editMessageById(any<Long>(), any<String>()) }
    }

    @Test
    fun `Given an id above the signed JDA range When deleting Then fail before issuing the REST call`() = runTest {
        val jda = mockk<JDA>()
        val channel = textChannel()
        every { jda.getTextChannelById(42L) } returns channel
        val port = JdaPlayerMessagePort(jda)
        val oversizedMessageId = PlayerMessageId(ULong.MAX_VALUE)

        val error = runCatching {
            port.delete(channelId, oversizedMessageId)
        }.exceptionOrNull()

        // Then: the guard stays, and the message is generic instead of echoing the snowflake.
        assertTrue(error is IllegalArgumentException)
        assertEquals("Message id does not fit in JDA's signed 64-bit id range", error?.message)
        assertFalse(error?.message.orEmpty().contains(oversizedMessageId.value.toString()))
        verify(exactly = 0) { channel.deleteMessageById(any<Long>()) }
    }

    /**
     * Every cached text channel fixture must resolve `isObfuscated`, which the port now reads, plus
     * the identity members a real JDA exception (for example [MissingAccessException]) reads when the
     * test constructs it over this same channel.
     */
    private fun textChannel(): TextChannel = mockk<TextChannel> {
        every { isObfuscated } returns false
        every { guild } returns mockk { every { idLong } returns GUILD_ID }
        every { idLong } returns CHANNEL_ID
        every { type } returns ChannelType.TEXT
    }

    private companion object {
        private const val GUILD_ID = 1L
        private const val CHANNEL_ID = 42L
        private const val MESSAGE_ID = 900L
        private const val RATE_LIMIT_RETRY_AFTER_MILLIS = 1_500L

        /** Value-free: an unresolvable channel id must never leak into a failure message. */
        private const val MISSING_CHANNEL_MESSAGE = "The requested player text channel is not available to the bot"
    }
}
