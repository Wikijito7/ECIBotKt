package es.wokis.jda.command

import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent
import net.dv8tion.jda.api.interactions.AutoCompleteQuery
import net.dv8tion.jda.api.interactions.commands.Command
import net.dv8tion.jda.api.requests.restaction.interactions.AutoCompleteCallbackAction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class JdaLocaleAutocompleteListenerTest {

    private val listener = JdaLocaleAutocompleteListener()

    private val action: AutoCompleteCallbackAction = mockk {
        every { queue() } just Runs
    }

    @Test
    fun `Given a partial canonical tag When autocomplete arrives Then return canonical matching choices`() {
        val choices = slot<Collection<Command.Choice>>()
        val event = event(query = "es")
        every { event.replyChoices(capture(choices)) } returns action

        listener.onCommandAutoCompleteInteraction(event)

        assertEquals(listOf("es-ES", "es-419"), choices.captured.map { it.name })
        assertTrue(choices.captured.all { it.asString == it.name })
    }

    @Test
    fun `Given lower-case input When autocomplete arrives Then match case-insensitively and return canonical spelling`() {
        val choices = slot<Collection<Command.Choice>>()
        val event = event(query = "en")
        every { event.replyChoices(capture(choices)) } returns action

        listener.onCommandAutoCompleteInteraction(event)

        assertEquals(listOf("en-GB", "en-US"), choices.captured.map { it.name })
    }

    @Test
    fun `Given Arabic or Hebrew input When autocomplete arrives Then expose those selectable tags`() {
        listOf("ar", "he").forEach { query ->
            val choices = slot<Collection<Command.Choice>>()
            val event = event(query = query)
            every { event.replyChoices(capture(choices)) } returns action

            listener.onCommandAutoCompleteInteraction(event)

            assertEquals(listOf(query), choices.captured.map { it.name }, query)
            assertEquals(listOf(query), choices.captured.map { it.asString }, query)
        }
    }

    @Test
    fun `Given an empty query When autocomplete arrives Then return at most 25 canonical tags`() {
        val choices = slot<Collection<Command.Choice>>()
        val event = event(query = "")
        every { event.replyChoices(capture(choices)) } returns action

        listener.onCommandAutoCompleteInteraction(event)

        assertEquals(25, choices.captured.size)
        assertEquals("ar", choices.captured.first().name)
        assertTrue(choices.captured.map { it.name }.none { it == "unknown" || it == "UNKNOWN" })
    }

    @Test
    fun `Given a query with no canonical match When autocomplete arrives Then reply with no choices`() {
        val choices = slot<Collection<Command.Choice>>()
        val event = event(query = "not-a-locale")
        every { event.replyChoices(capture(choices)) } returns action

        listener.onCommandAutoCompleteInteraction(event)

        assertTrue(choices.captured.isEmpty())
    }

    @Test
    fun `Given another command When autocomplete arrives Then ignore the interaction`() {
        val event = event(commandName = "jda-walkthrough", query = "es")

        listener.onCommandAutoCompleteInteraction(event)

        verify(exactly = 0) { event.replyChoices(any<Collection<Command.Choice>>()) }
    }

    @Test
    fun `Given a command with another focused option When autocomplete arrives Then reply with no choices`() {
        val choices = slot<Collection<Command.Choice>>()
        val event = event(optionName = "other", query = "es")
        every { event.replyChoices(capture(choices)) } returns action

        listener.onCommandAutoCompleteInteraction(event)

        assertTrue(choices.captured.isEmpty())
    }

    private fun event(
        commandName: String = "locale",
        optionName: String = "locale",
        query: String = "",
    ): CommandAutoCompleteInteractionEvent {
        val queryOption = mockk<AutoCompleteQuery> {
            every { name } returns optionName
            every { value } returns query
        }
        return mockk<CommandAutoCompleteInteractionEvent> {
            every { name } returns commandName
            every { focusedOption } returns queryOption
            every { replyChoices(any<Collection<Command.Choice>>()) } returns action
        }
    }
}
