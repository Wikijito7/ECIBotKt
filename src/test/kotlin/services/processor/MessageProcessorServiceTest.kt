package services.processor

import dev.kord.common.entity.Snowflake
import dev.kord.core.entity.Message
import dev.kord.core.entity.ReactionEmoji
import es.wokis.dispatchers.AppDispatchers
import es.wokis.services.processor.MessageProcessorService
import io.mockk.*
import mock.MockedDispatchers
import org.junit.jupiter.api.Test

class MessageProcessorServiceTest {

    private val dispatchers: AppDispatchers = mockk {
        every { io } returns MockedDispatchers.io
    }

    private val messageProcessor = MessageProcessorService(dispatchers)

    @Test
    fun `Given message with twitter url When processMessage is called Then process message`() {
        // Given
        val originalMessage = "https://twitter.com/blablabla"
        val authorId = Snowflake(123123)
        val authorMention = "manolete"
        val message: Message = mockk {
            every { content } returns originalMessage
            every { author?.id } returns authorId
            every { author?.mention } returns authorMention
        }
        val editedMessage = "Post enviado por $authorMention con el enlace arreglado:\nhttps://fixupx.com/blablabla"

        coJustRun { message.delete() }
        coJustRun { message.channel.createMessage(any()) }

        // When
        messageProcessor.processMessage(message)

        // Then
        coVerify(exactly = 1) {
            message.delete()
            message.channel.createMessage(editedMessage)
        }
    }

    @Test
    fun `Given message with reddit url When processMessage is called Then process message`() {
        // Given
        val originalMessage = "https://reddit.com/blablabla"
        val authorId = Snowflake(123123)
        val authorMention = "manolete"
        val message: Message = mockk {
            every { content } returns originalMessage
            every { author?.id } returns authorId
            every { author?.mention } returns authorMention
        }
        val editedMessage = "Post enviado por $authorMention con el enlace arreglado:\nhttps://rxddit.com/blablabla"

        coJustRun { message.delete() }
        coJustRun { message.channel.createMessage(any()) }

        // When
        messageProcessor.processMessage(message)

        // Then
        coVerify(exactly = 1) {
            message.delete()
            message.channel.createMessage(editedMessage)
        }
    }

    @Test
    fun `Given message with instagram url When processMessage is called Then process message`() {
        // Given
        val originalMessage = "https://instagram.com/blablabla"
        val authorId = Snowflake(123123)
        val authorMention = "manolete"
        val message: Message = mockk {
            every { content } returns originalMessage
            every { author?.id } returns authorId
            every { author?.mention } returns authorMention
        }
        val editedMessage = "Post enviado por $authorMention con el enlace arreglado:\nhttps://ddinstagram.com/blablabla"

        coJustRun { message.delete() }
        coJustRun { message.channel.createMessage(any()) }

        // When
        messageProcessor.processMessage(message)

        // Then
        coVerify(exactly = 1) {
            message.delete()
            message.channel.createMessage(editedMessage)
        }
    }

    @Test
    fun `Given message with tiktok url When processMessage is called Then process message`() {
        // Given
        val originalMessage = "https://tiktok.com/blablabla"
        val authorId = Snowflake(123123)
        val authorMention = "manolete"
        val message: Message = mockk {
            every { content } returns originalMessage
            every { author?.id } returns authorId
            every { author?.mention } returns authorMention
        }
        val editedMessage = "Post enviado por $authorMention con el enlace arreglado:\nhttps://tnktok.com/blablabla"

        coJustRun { message.delete() }
        coJustRun { message.channel.createMessage(any()) }

        // When
        messageProcessor.processMessage(message)

        // Then
        coVerify(exactly = 1) {
            message.delete()
            message.channel.createMessage(editedMessage)
        }
    }

    @Test
    fun `Given message with other url When processMessage is called Then leave original message`() {
        // Given
        val originalMessage = "https://wokis.es/blablabla"
        val authorId = Snowflake(123123)
        val authorMention = "manolete"
        val message: Message = mockk {
            every { content } returns originalMessage
            every { author?.id } returns authorId
            every { author?.mention } returns authorMention
        }

        coJustRun { message.delete() }
        coJustRun { message.channel.createMessage(any()) }

        // When
        messageProcessor.processMessage(message)

        // Then
        coVerify(exactly = 0) {
            message.delete()
            message.channel.createMessage(any())
        }
    }

    @Test
    fun `Given message with Isaac author id When processReactions is called Then add reactions`() {
        // Given
        val originalMessage = "Lo que sea de mensaje"
        val authorId = Snowflake(378213328570417154)
        val authorMention = "manolete"
        val message: Message = mockk {
            every { content } returns originalMessage
            every { author?.id } returns authorId
            every { author?.mention } returns authorMention
        }
        val reaction = ReactionEmoji.Unicode("🍆")

        coJustRun { message.addReaction(any<ReactionEmoji>()) }

        // When
        messageProcessor.processReactions(message)

        // Then
        coVerify(exactly = 1) {
            message.addReaction(reaction)
        }
    }

    @Test
    fun `Given message with Fran author id When processReactions is called Then add reactions`() {
        // Given
        val originalMessage = "Lo que sea de mensaje"
        val authorId = Snowflake(651163679814844467)
        val authorMention = "manolete"
        val message: Message = mockk {
            every { content } returns originalMessage
            every { author?.id } returns authorId
            every { author?.mention } returns authorMention
        }
        val reaction = ReactionEmoji.Unicode("😢")

        coJustRun { message.addReaction(any<ReactionEmoji>()) }

        // When
        messageProcessor.processReactions(message)

        // Then
        coVerify(exactly = 1) {
            message.addReaction(reaction)
        }
    }

    @Test
    fun `Given message with Guti author id When processReactions is called Then add reactions`() {
        // Given
        val originalMessage = "Lo que sea de mensaje"
        val authorId = Snowflake(899918332965298176)
        val authorMention = "manolete"
        val message: Message = mockk {
            every { content } returns originalMessage
            every { author?.id } returns authorId
            every { author?.mention } returns authorMention
        }
        val reaction = ReactionEmoji.Unicode("😭")

        coJustRun { message.addReaction(any<ReactionEmoji>()) }

        // When
        messageProcessor.processReactions(message)

        // Then
        coVerify(exactly = 1) {
            message.addReaction(reaction)
        }
    }

    @Test
    fun `Given message with France mentioned When processReactions is called Then add reactions`() {
        // Given
        val originalMessage = "odio francia"
        val authorId = Snowflake(123123123)
        val authorMention = "manolete"
        val message: Message = mockk {
            every { content } returns originalMessage
            every { author?.id } returns authorId
            every { author?.mention } returns authorMention
        }
        val reactions = listOf("🇫🇷", "🥖", "🥐", "🍷")

        coJustRun { message.addReaction(any<ReactionEmoji>()) }

        // When
        messageProcessor.processReactions(message)

        // Then
        reactions.map { ReactionEmoji.Unicode(it) }.forEach {
            coVerify(exactly = 1) {
                message.addReaction(it)
            }
        }
    }

    @Test
    fun `Given message with Spain mentioned When processReactions is called Then add reactions`() {
        // Given
        val originalMessage = "viva españa"
        val authorId = Snowflake(123123123)
        val authorMention = "manolete"
        val message: Message = mockk {
            every { content } returns originalMessage
            every { author?.id } returns authorId
            every { author?.mention } returns authorMention
        }
        val reactions = listOf("🆙", "🇪🇸", "❤️‍🔥", "💃", "🥘", "🏖️", "🛌", "🇪🇦")

        coJustRun { message.addReaction(any<ReactionEmoji>()) }

        // When
        messageProcessor.processReactions(message)

        // Then
        reactions.map { ReactionEmoji.Unicode(it) }.forEach {
            coVerify(exactly = 1) {
                message.addReaction(it)
            }
        }
    }

    @Test
    fun `Given message with Mexico mentioned When processReactions is called Then add reactions`() {
        // Given
        val originalMessage = "viva mexico cabrones"
        val authorId = Snowflake(123123123)
        val authorMention = "manolete"
        val message: Message = mockk {
            every { content } returns originalMessage
            every { author?.id } returns authorId
            every { author?.mention } returns authorMention
        }
        val reactions = listOf("🇲🇽", "🌯", "🌮", "🫔")

        coJustRun { message.addReaction(any<ReactionEmoji>()) }

        // When
        messageProcessor.processReactions(message)

        // Then
        reactions.map { ReactionEmoji.Unicode(it) }.forEach {
            coVerify(exactly = 1) {
                message.addReaction(it)
            }
        }
    }

    @Test
    fun `Given regular When processReactions is called Then don't add any reactions`() {
        // Given
        val originalMessage = "pepe"
        val authorId = Snowflake(123123123)
        val authorMention = "manolete"
        val message: Message = mockk {
            every { content } returns originalMessage
            every { author?.id } returns authorId
            every { author?.mention } returns authorMention
        }

        coJustRun { message.addReaction(any<ReactionEmoji>()) }

        // When
        messageProcessor.processReactions(message)

        // Then
        coVerify(exactly = 0) {
            message.addReaction(any<ReactionEmoji>())
        }
    }
}
