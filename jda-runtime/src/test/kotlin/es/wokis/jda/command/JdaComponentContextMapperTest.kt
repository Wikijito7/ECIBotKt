package es.wokis.jda.command

import es.wokis.commands.neutral.ComponentContext
import es.wokis.domain.locale.LocaleCode
import es.wokis.domain.model.ChannelId
import es.wokis.domain.model.GuildId
import es.wokis.domain.model.UserId
import es.wokis.message.ComponentId
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.interactions.DiscordLocale
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class JdaComponentContextMapperTest {

    private val mapper = JdaComponentContextMapper()

    @Test
    fun `Given a guild button When mapped Then map the guild channel and user ids`() {
        // Given
        val event = event(guildId = 10L, channelId = 20L, userId = 30L)

        // When
        val context = mapper.map(event, ComponentId.PLAYER_PAUSE)

        // Then
        assertEquals(ComponentId.PLAYER_PAUSE, context.componentId)
        assertEquals(GuildId(10uL), context.guildId)
        assertEquals(ChannelId(20uL), context.channelId)
        assertEquals(UserId(30uL), context.userId)
    }

    @Test
    fun `Given a guild default and a different user locale When mapped Then prefer the guild default`() {
        // Given
        val event = event(
            guildId = 10L,
            guildLocale = DiscordLocale.SPANISH,
            userLocale = DiscordLocale.GERMAN,
        )

        // When
        val context = mapper.map(event, ComponentId.PLAYER_RESUME)

        // Then the resolved locale follows the guild default, not the client preference
        assertEquals(LocaleCode("es-ES"), context.locale)
        assertEquals(LocaleCode("es-ES"), context.guildLocale)
        assertEquals(LocaleCode("de"), context.userLocale)
    }

    @Test
    fun `Given an unknown guild default When mapped Then fall back to the user locale`() {
        // Given
        val event = event(
            guildId = 10L,
            guildLocale = DiscordLocale.UNKNOWN,
            userLocale = DiscordLocale.GERMAN,
        )

        // When
        val context = mapper.map(event, ComponentId.PLAYER_SKIP)

        // Then
        assertEquals(LocaleCode("de"), context.locale)
        assertNull(context.guildLocale)
        assertEquals(LocaleCode("de"), context.userLocale)
    }

    @Test
    fun `Given both locales unknown When mapped Then expose null locale fields`() {
        // Given
        val event = event(
            guildId = 10L,
            guildLocale = DiscordLocale.UNKNOWN,
            userLocale = DiscordLocale.UNKNOWN,
        )

        // When
        val context = mapper.map(event, ComponentId.PLAYER_SHUFFLE)

        // Then
        assertNull(context.locale)
        assertNull(context.guildLocale)
        assertNull(context.userLocale)
    }

    @Test
    fun `Given a direct message When mapped Then expose a null guild and the user locale`() {
        // Given
        val event = event(guildId = null, userLocale = DiscordLocale.SPANISH_LATAM)

        // When
        val context = mapper.map(event, ComponentId.PLAYER_DISCONNECT)

        // Then
        assertNull(context.guildId)
        assertEquals(ChannelId(20uL), context.channelId)
        assertEquals(UserId(30uL), context.userId)
        assertEquals(LocaleCode("es-419"), context.locale)
        assertNull(context.guildLocale)
        assertEquals(LocaleCode("es-419"), context.userLocale)
    }

    @Test
    fun `Given a direct message with an unknown user locale When mapped Then expose null locales`() {
        // Given
        val event = event(guildId = null, userLocale = DiscordLocale.UNKNOWN)

        // When
        val context = mapper.map(event, ComponentId.PLAYER_RECONNECT)

        // Then
        assertNull(context.guildId)
        assertNull(context.locale)
        assertNull(context.guildLocale)
        assertNull(context.userLocale)
    }

    @Test
    fun `Given negative snowflake bits When mapped Then preserve the full unsigned value`() {
        // Given: JDA exposes snowflakes as signed longs, so values above Long.MAX_VALUE arrive negative.
        val event = event(guildId = -1L, channelId = -2L, userId = -3L)

        // When
        val context = mapper.map(event, ComponentId.QUEUE_NEXT)

        // Then the mapper never narrows, rejects or clamps the unsigned snowflake
        assertEquals(GuildId(ULong.MAX_VALUE), context.guildId)
        assertEquals(ChannelId((-2L).toULong()), context.channelId)
        assertEquals(UserId((-3L).toULong()), context.userId)
    }

    @Test
    fun `Given positive snowflakes at the signed maximum When mapped Then keep them exact`() {
        // Given
        val event = event(guildId = Long.MAX_VALUE, channelId = Long.MAX_VALUE, userId = Long.MAX_VALUE)

        // When
        val context = mapper.map(event, ComponentId.QUEUE_PREVIOUS)

        // Then
        assertEquals(GuildId(Long.MAX_VALUE.toULong()), context.guildId)
        assertEquals(ChannelId(Long.MAX_VALUE.toULong()), context.channelId)
        assertEquals(UserId(Long.MAX_VALUE.toULong()), context.userId)
    }

    @Test
    fun `Given a button without optional data When mapped Then return the option-free context`() {
        // Given
        val event = event(guildId = 10L, guildLocale = DiscordLocale.ENGLISH_UK, userLocale = DiscordLocale.ENGLISH_UK)
        val expected = ComponentContext(
            componentId = ComponentId.RADIO_LIST_NEXT,
            guildId = GuildId(10uL),
            channelId = ChannelId(20uL),
            userId = UserId(30uL),
            locale = LocaleCode("en-GB"),
            guildLocale = LocaleCode("en-GB"),
            userLocale = LocaleCode("en-GB"),
        )

        // When
        val context = mapper.map(event, ComponentId.RADIO_LIST_NEXT)

        // Then data-class equality also proves no extra interaction data is required to map
        assertEquals(expected, context)
    }

    @Test
    fun `Given every component id When mapped Then carry the resolved id untouched`() {
        // Given
        val event = event(guildId = 10L)

        // When / Then
        ComponentId.entries.forEach { id ->
            assertEquals(id, mapper.map(event, id).componentId, id.name)
        }
    }

    @Test
    fun `Given a guild button When mapped Then never resolve a member`() {
        // Given: JDA exposes a nullable member, but the mapper must only read ids and locales.
        val event = event(guildId = 10L)
        every { event.member } returns null

        // When
        val context = mapper.map(event, ComponentId.SOUNDS_NEXT)

        // Then
        assertEquals(GuildId(10uL), context.guildId)
        verify(exactly = 0) { event.member }
    }

    private fun event(
        guildId: Long?,
        guildLocale: DiscordLocale = DiscordLocale.ENGLISH_US,
        userLocale: DiscordLocale = DiscordLocale.ENGLISH_US,
        channelId: Long = 20L,
        userId: Long = 30L,
    ): ButtonInteractionEvent {
        val guild = guildId?.let { id ->
            mockk<Guild> {
                every { idLong } returns id
                every { locale } returns guildLocale
            }
        }
        val channel = mockk<MessageChannelUnion> {
            every { idLong } returns channelId
        }
        val user = mockk<User> {
            every { idLong } returns userId
        }

        return mockk<ButtonInteractionEvent>().also { event ->
            every { event.guild } returns guild
            every { event.channel } returns channel
            every { event.user } returns user
            every { event.userLocale } returns userLocale
        }
    }
}
