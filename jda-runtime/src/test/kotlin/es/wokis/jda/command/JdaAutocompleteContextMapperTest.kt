package es.wokis.jda.command

import es.wokis.commands.neutral.AutocompleteContext
import es.wokis.domain.model.GuildId
import io.mockk.every
import io.mockk.mockk
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent
import net.dv8tion.jda.api.interactions.AutoCompleteQuery
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class JdaAutocompleteContextMapperTest {

    private val mapper = JdaAutocompleteContextMapper()

    @Test
    fun `Given a guild autocomplete event When mapped Then carry command option guild and focused value`() {
        val guild: Guild = mockk {
            every { idLong } returns GUILD_ID
        }

        val context = mapper.map(
            event(commandName = "sound", optionName = "name", focusedValue = "rain", guildOrNull = guild),
        )

        assertEquals(
            AutocompleteContext(
                commandName = "sound",
                optionName = "name",
                guildId = GuildId(GUILD_ID.toULong()),
                input = "rain",
            ),
            context,
        )
    }

    @Test
    fun `Given a direct message autocomplete event When mapped Then carry a null guild`() {
        val context = mapper.map(
            event(commandName = "locale", optionName = "locale", focusedValue = "es", guildOrNull = null),
        )

        assertNull(context.guildId)
        assertEquals("locale", context.commandName)
        assertEquals("locale", context.optionName)
        assertEquals("es", context.input)
    }

    @Test
    fun `Given an empty focused value When mapped Then carry the empty input`() {
        val guild: Guild = mockk {
            every { idLong } returns GUILD_ID
        }

        val context = mapper.map(
            event(commandName = "sound", optionName = "name", focusedValue = "", guildOrNull = guild),
        )

        assertEquals("", context.input)
    }

    @Test
    fun `Given a tts autocomplete event When mapped Then carry the free text message input`() {
        val context = mapper.map(
            event(commandName = "tts", optionName = "message", focusedValue = "hola mundo", guildOrNull = null),
        )

        assertEquals("tts", context.commandName)
        assertEquals("message", context.optionName)
        assertEquals("hola mundo", context.input)
    }

    private fun event(
        commandName: String,
        optionName: String,
        focusedValue: String,
        guildOrNull: Guild?,
    ): CommandAutoCompleteInteractionEvent {
        val query = mockk<AutoCompleteQuery> {
            every { name } returns optionName
            every { value } returns focusedValue
        }
        return mockk {
            every { name } returns commandName
            every { focusedOption } returns query
            every { guild } returns guildOrNull
        }
    }

    private companion object {
        private const val GUILD_ID = 42L
    }
}
