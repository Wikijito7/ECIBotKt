package es.wokis.jda.command

import es.wokis.domain.locale.LocaleCode
import es.wokis.domain.model.ChannelId
import es.wokis.domain.model.GuildId
import es.wokis.domain.model.UserId
import io.mockk.every
import io.mockk.mockk
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.DiscordLocale
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class JdaCommandContextMapperTest {

    private val mapper = JdaCommandContextMapper()

    @Test
    fun `Given guild event When mapped Then use guild id and guild locale`() {
        // Given
        val event = event(
            guildId = 10L,
            guildLocale = DiscordLocale.SPANISH,
            userLocale = DiscordLocale.GERMAN,
        )

        // When
        val context = mapper.map(event)

        // Then
        assertEquals("jda-walkthrough", context.commandName)
        assertEquals(GuildId(10uL), context.guildId)
        assertEquals(ChannelId(20uL), context.channelId)
        assertEquals(UserId(30uL), context.userId)
        assertEquals(LocaleCode("es-ES"), context.locale)
    }

    @Test
    fun `Given guild locale UNKNOWN When mapped Then fall back to the user locale`() {
        // Given
        val event = event(
            guildId = 10L,
            guildLocale = DiscordLocale.UNKNOWN,
            userLocale = DiscordLocale.GERMAN,
        )

        // When
        val context = mapper.map(event)

        // Then
        assertEquals(GuildId(10uL), context.guildId)
        assertEquals(LocaleCode("de"), context.locale)
    }

    @Test
    fun `Given guild and user locales UNKNOWN When mapped Then expose a null neutral locale`() {
        // Given
        val event = event(
            guildId = 10L,
            guildLocale = DiscordLocale.UNKNOWN,
            userLocale = DiscordLocale.UNKNOWN,
        )

        // When
        val context = mapper.map(event)

        // Then
        assertNull(context.locale)
    }

    @Test
    fun `Given direct message When mapped Then use a null guild and the user locale`() {
        // Given
        val event = event(
            guildId = null,
            userLocale = DiscordLocale.SPANISH_LATAM,
        )

        // When
        val context = mapper.map(event)

        // Then
        assertNull(context.guildId)
        assertEquals(ChannelId(20uL), context.channelId)
        assertEquals(UserId(30uL), context.userId)
        assertEquals(LocaleCode("es-419"), context.locale)
    }

    @Test
    fun `Given direct message with UNKNOWN user locale When mapped Then expose a null neutral locale`() {
        // Given
        val event = event(
            guildId = null,
            userLocale = DiscordLocale.UNKNOWN,
        )

        // When
        val context = mapper.map(event)

        // Then
        assertNull(context.guildId)
        assertNull(context.locale)
    }

    @Test
    fun `Given negative snowflake bits When mapped Then preserve the full unsigned value`() {
        // Given
        val event = event(
            guildId = -1L,
            channelId = -2L,
            userId = -3L,
        )

        // When
        val context = mapper.map(event)

        // Then
        assertEquals(GuildId(ULong.MAX_VALUE), context.guildId)
        assertEquals(ChannelId((-2L).toULong()), context.channelId)
        assertEquals(UserId((-3L).toULong()), context.userId)
    }

    private fun event(
        guildId: Long?,
        guildLocale: DiscordLocale = DiscordLocale.ENGLISH_US,
        userLocale: DiscordLocale = DiscordLocale.ENGLISH_US,
        channelId: Long = 20L,
        userId: Long = 30L,
        commandName: String = "jda-walkthrough",
    ): SlashCommandInteractionEvent {
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

        return mockk<SlashCommandInteractionEvent>().also { event ->
            every { event.name } returns commandName
            every { event.guild } returns guild
            every { event.channel } returns channel
            every { event.user } returns user
            every { event.userLocale } returns userLocale
        }
    }
}
