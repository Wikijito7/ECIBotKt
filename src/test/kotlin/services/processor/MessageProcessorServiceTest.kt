package services.processor

import dev.kord.common.Locale
import dev.kord.common.entity.Snowflake
import dev.kord.core.entity.Message
import dev.kord.core.entity.ReactionEmoji
import dev.kord.core.supplier.EntitySupplyStrategy
import es.wokis.dispatchers.AppDispatchers
import es.wokis.services.localization.LocalizationService
import es.wokis.services.processor.MessageProcessorService
import io.mockk.*
import mock.TestDispatchers
import org.junit.jupiter.api.Test

class MessageProcessorServiceTest {

    private val dispatchers: AppDispatchers = TestDispatchers()
    private val localizationService: LocalizationService = mockk()

    private val messageProcessor = MessageProcessorService(
        appDispatchers = dispatchers,
        localizationService = localizationService
    )

    @Test
    fun `Given message with twitter status url When processMessage is called Then process message`() {
        // Given
        val originalMessage = "https://twitter.com/status/blablabla"
        val authorId = Snowflake(123123)
        val authorMention = "manolete"
        val message: Message = mockk {
            every { content } returns originalMessage
            every { author?.id } returns authorId
            every { author?.mention } returns authorMention
            every { data } returns mockk {
                every { guildId.value } returns Snowflake(123)
            }
            every { kord } returns mockk {
                every { resources } returns mockk {
                    every { defaultStrategy } returns EntitySupplyStrategy.rest
                }
                coEvery { getGuildOrNull(any()) } returns mockk {
                    every { preferredLocale } returns Locale.ENGLISH_UNITED_STATES
                }
            }
        }
        val editedMessage = "Post enviado por $authorMention con el enlace arreglado:\nhttps://fixupx.com/status/blablabla"

        coEvery { localizationService.getStringFormat(any(), any(), *anyVararg()) } returns editedMessage
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
    fun `Given message with regular twitter url When processMessage is called Then don't process message`() {
        // Given
        val originalMessage = "https://twitter.com/blablabla"
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
    fun `Given message with reddit url When processMessage is called Then process message`() {
        // Given
        val originalMessage = "https://reddit.com/blablabla"
        val authorId = Snowflake(123123)
        val authorMention = "manolete"
        val message: Message = mockk {
            every { content } returns originalMessage
            every { author?.id } returns authorId
            every { author?.mention } returns authorMention
            every { data } returns mockk {
                every { guildId.value } returns Snowflake(123)
            }
            every { kord } returns mockk {
                every { resources } returns mockk {
                    every { defaultStrategy } returns EntitySupplyStrategy.rest
                }
                coEvery { getGuildOrNull(any()) } returns mockk {
                    every { preferredLocale } returns Locale.ENGLISH_UNITED_STATES
                }
            }
        }
        val editedMessage = "Post enviado por $authorMention con el enlace arreglado:\nhttps://rxddit.com/blablabla"

        coEvery { localizationService.getStringFormat(any(), any(), *anyVararg()) } returns editedMessage
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
    fun `Given message with instagram picture url When processMessage is called Then process message`() {
        // Given
        val originalMessage = "https://instagram.com/p/blablabla"
        val authorId = Snowflake(123123)
        val authorMention = "manolete"
        val message: Message = mockk {
            every { content } returns originalMessage
            every { author?.id } returns authorId
            every { author?.mention } returns authorMention
            every { data } returns mockk {
                every { guildId.value } returns Snowflake(123)
            }
            every { kord } returns mockk {
                every { resources } returns mockk {
                    every { defaultStrategy } returns EntitySupplyStrategy.rest
                }
                coEvery { getGuildOrNull(any()) } returns mockk {
                    every { preferredLocale } returns Locale.ENGLISH_UNITED_STATES
                }
            }
        }
        val editedMessage = "Post enviado por $authorMention con el enlace arreglado:\nhttps://ddinstagram.com/p/blablabla"

        coEvery { localizationService.getStringFormat(any(), any(), *anyVararg()) } returns editedMessage
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
    fun `Given message with regular instagram url When processMessage is called Then don't process message`() {
        // Given
        val originalMessage = "https://instagram.com/blablabla"
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
    fun `Given message with tiktok url When processMessage is called Then process message`() {
        // Given
        val originalMessage = "https://tiktok.com/blablabla"
        val authorId = Snowflake(123123)
        val authorMention = "manolete"
        val message: Message = mockk {
            every { content } returns originalMessage
            every { author?.id } returns authorId
            every { author?.mention } returns authorMention
            every { data } returns mockk {
                every { guildId.value } returns Snowflake(123)
            }
            every { kord } returns mockk {
                every { resources } returns mockk {
                    every { defaultStrategy } returns EntitySupplyStrategy.rest
                }
                coEvery { getGuildOrNull(any()) } returns mockk {
                    every { preferredLocale } returns Locale.ENGLISH_UNITED_STATES
                }
            }
        }
        val editedMessage = "Post enviado por $authorMention con el enlace arreglado:\nhttps://vxtiktok.com/blablabla"

        coEvery { localizationService.getStringFormat(any(), any(), *anyVararg()) } returns editedMessage
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
    fun `Given message with vm tiktok url When processMessage is called Then process message`() {
        // Given
        val originalMessage = "https://vm.tiktok.com/blablabla"
        val authorId = Snowflake(123123)
        val authorMention = "manolete"
        val message: Message = mockk {
            every { content } returns originalMessage
            every { author?.id } returns authorId
            every { author?.mention } returns authorMention
            every { data } returns mockk {
                every { guildId.value } returns Snowflake(123)
            }
            every { kord } returns mockk {
                every { resources } returns mockk {
                    every { defaultStrategy } returns EntitySupplyStrategy.rest
                }
                coEvery { getGuildOrNull(any()) } returns mockk {
                    every { preferredLocale } returns Locale.ENGLISH_UNITED_STATES
                }
            }
        }
        val editedMessage = "Post enviado por $authorMention con el enlace arreglado:\nhttps://vm.vxtiktok.com/blablabla"

        coEvery { localizationService.getStringFormat(any(), any(), *anyVararg()) } returns editedMessage
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
