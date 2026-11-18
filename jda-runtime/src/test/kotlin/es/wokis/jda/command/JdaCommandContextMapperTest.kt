package es.wokis.jda.command

import es.wokis.domain.locale.DiscordLocaleCatalog
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
import net.dv8tion.jda.api.interactions.commands.OptionMapping
import net.dv8tion.jda.api.interactions.commands.OptionType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class JdaCommandContextMapperTest {

    private val mapper = JdaCommandContextMapper()

    @Test
    fun `Given guild event When mapped Then use guild id and guild locale`() {
        val event = event(
            guildId = 10L,
            guildLocale = DiscordLocale.SPANISH,
            userLocale = DiscordLocale.GERMAN,
        )

        val context = mapper.map(event)

        assertEquals("jda-walkthrough", context.commandName)
        assertEquals(GuildId(10uL), context.guildId)
        assertEquals(ChannelId(20uL), context.channelId)
        assertEquals(UserId(30uL), context.userId)
        assertEquals(LocaleCode("es-ES"), context.locale)
        assertEquals(LocaleCode("es-ES"), context.guildLocale)
        assertEquals(LocaleCode("de"), context.userLocale)
    }

    @Test
    fun `Given guild locale UNKNOWN When mapped Then fall back to the user locale`() {
        val event = event(
            guildId = 10L,
            guildLocale = DiscordLocale.UNKNOWN,
            userLocale = DiscordLocale.GERMAN,
        )

        val context = mapper.map(event)

        assertEquals(GuildId(10uL), context.guildId)
        assertEquals(LocaleCode("de"), context.locale)
        assertNull(context.guildLocale)
        assertEquals(LocaleCode("de"), context.userLocale)
    }

    @Test
    fun `Given Arabic and Hebrew locales When mapped Then expose the canonical neutral tags`() {
        val event = event(
            guildId = 10L,
            guildLocale = DiscordLocale.ARABIC,
            userLocale = DiscordLocale.HEBREW,
        )

        val context = mapper.map(event)

        assertEquals(LocaleCode("ar"), context.guildLocale)
        assertEquals(LocaleCode("he"), context.userLocale)
        assertEquals(LocaleCode("ar"), context.locale)
    }

    @Test
    fun `Given every real JDA locale When mapped Then use the canonical tag and detect catalog drift`() {
        val realLocales = DiscordLocale.values().filter { it != DiscordLocale.UNKNOWN }

        // Then the JDA enum and the neutral catalog describe the same 34 canonical tags
        assertEquals(34, realLocales.size)
        assertEquals(
            realLocales.map { it.locale }.toSet(),
            DiscordLocaleCatalog.supportedLocales.map { it.value }.toSet(),
        )

        // And every real locale maps through its canonical string
        realLocales.forEach { locale ->
            assertEquals(LocaleCode(locale.locale), locale.toLocaleCodeOrNull(), locale.name)
        }
    }

    @Test
    fun `Given JDA UNKNOWN When mapped Then produce null instead of the unknown marker`() {
        assertNull(DiscordLocale.UNKNOWN.toLocaleCodeOrNull())
        assertNull((null as DiscordLocale?).toLocaleCodeOrNull())
    }

    @Test
    fun `Given guild and user locales UNKNOWN When mapped Then expose a null neutral locale`() {
        val event = event(
            guildId = 10L,
            guildLocale = DiscordLocale.UNKNOWN,
            userLocale = DiscordLocale.UNKNOWN,
        )

        val context = mapper.map(event)

        assertNull(context.locale)
        assertNull(context.guildLocale)
        assertNull(context.userLocale)
    }

    @Test
    fun `Given direct message When mapped Then use a null guild and the user locale`() {
        val event = event(
            guildId = null,
            userLocale = DiscordLocale.SPANISH_LATAM,
        )

        val context = mapper.map(event)

        assertNull(context.guildId)
        assertEquals(ChannelId(20uL), context.channelId)
        assertEquals(UserId(30uL), context.userId)
        assertEquals(LocaleCode("es-419"), context.locale)
        assertNull(context.guildLocale)
        assertEquals(LocaleCode("es-419"), context.userLocale)
    }

    @Test
    fun `Given direct message with UNKNOWN user locale When mapped Then expose a null neutral locale`() {
        val event = event(
            guildId = null,
            userLocale = DiscordLocale.UNKNOWN,
        )

        val context = mapper.map(event)

        assertNull(context.guildId)
        assertNull(context.locale)
        assertNull(context.guildLocale)
        assertNull(context.userLocale)
    }

    @Test
    fun `Given string and non-string options When mapped Then expose only string options`() {
        val stringOption = mockk<OptionMapping> {
            every { type } returns OptionType.STRING
            every { name } returns "locale"
            every { asString } returns "es-ES"
        }
        val integerOption = mockk<OptionMapping> {
            every { type } returns OptionType.INTEGER
            every { name } returns "count"
        }
        val event = event(
            guildId = 10L,
            options = listOf(stringOption, integerOption),
        )

        val context = mapper.map(event)

        assertEquals(mapOf("locale" to "es-ES"), context.stringOptions)
    }

    @Test
    fun `Given negative snowflake bits When mapped Then preserve the full unsigned value`() {
        val event = event(
            guildId = -1L,
            channelId = -2L,
            userId = -3L,
        )

        val context = mapper.map(event)

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
        options: List<OptionMapping> = emptyList(),
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
            every { event.options } returns options
        }
    }
}
