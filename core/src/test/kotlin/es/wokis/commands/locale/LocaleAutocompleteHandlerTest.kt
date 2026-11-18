package es.wokis.commands.locale

import es.wokis.commands.neutral.AutocompleteContext
import es.wokis.commands.neutral.AutocompleteResponder
import es.wokis.domain.model.GuildId
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LocaleAutocompleteHandlerTest {

    private val handler = LocaleAutocompleteHandler()

    @Test
    fun `Given the locale handler When inspected Then it owns Kord's command and option`() {
        assertEquals(LocaleCommandHandler.COMMAND_NAME, handler.commandName)
        assertEquals(LocaleCommandHandler.OPTION_NAME, handler.optionName)
        assertEquals("locale", handler.commandName)
        assertEquals("locale", handler.optionName)
    }

    @Test
    fun `Given a partial canonical tag When autocomplete runs Then reply the canonical matches in catalog order`() = runTest {
        val responder = RecordingResponder()

        handler.handle(context("es"), responder)

        assertEquals(listOf(listOf("es-ES", "es-419")), responder.replies)
    }

    @Test
    fun `Given lower-case input When autocomplete runs Then match case-insensitively and keep the canonical spelling`() = runTest {
        val responder = RecordingResponder()

        handler.handle(context("en"), responder)

        assertEquals(listOf(listOf("en-GB", "en-US")), responder.replies)
    }

    @Test
    fun `Given a canonical tag typed in another case When autocomplete runs Then reply the canonical spelling`() = runTest {
        val responder = RecordingResponder()

        handler.handle(context("ES-es"), responder)

        assertEquals(listOf(listOf("es-ES")), responder.replies)
    }

    @Test
    fun `Given Arabic or Hebrew input When autocomplete runs Then expose those selectable tags`() = runTest {
        listOf("ar", "he").forEach { query ->
            val responder = RecordingResponder()

            handler.handle(context(query), responder)

            assertEquals(listOf(listOf(query)), responder.replies, query)
        }
    }

    @Test
    fun `Given an empty query When autocomplete runs Then reply at most 25 canonical tags starting at the first`() = runTest {
        val responder = RecordingResponder()

        handler.handle(context(""), responder)

        val suggestions = responder.replies.single()
        assertEquals(25, suggestions.size)
        assertEquals("ar", suggestions.first())
        assertTrue(suggestions.none { it == "unknown" || it == "UNKNOWN" })
    }

    @Test
    fun `Given a query with no canonical match When autocomplete runs Then reply with no choices`() = runTest {
        val responder = RecordingResponder()

        handler.handle(context("not-a-locale"), responder)

        assertEquals(listOf(emptyList<String>()), responder.replies)
    }

    @Test
    fun `Given a direct message context When autocomplete runs Then suggest identically without a guild`() = runTest {
        val responder = RecordingResponder()

        handler.handle(context("es", guildId = null), responder)

        assertEquals(listOf(listOf("es-ES", "es-419")), responder.replies)
    }

    private fun context(input: String, guildId: GuildId? = GuildId(42uL)) =
        AutocompleteContext(
            commandName = LocaleCommandHandler.COMMAND_NAME,
            optionName = LocaleCommandHandler.OPTION_NAME,
            guildId = guildId,
            input = input,
        )

    private class RecordingResponder : AutocompleteResponder {

        val replies = mutableListOf<List<String>>()

        override suspend fun respond(suggestions: List<String>) {
            replies += suggestions
        }
    }
}
