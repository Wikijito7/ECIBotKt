package es.wokis.jda.command

import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent
import net.dv8tion.jda.api.interactions.commands.Command
import net.dv8tion.jda.api.requests.restaction.interactions.AutoCompleteCallbackAction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.function.Consumer
import kotlin.test.assertFailsWith

class JdaAutocompleteResponderTest {

    private val delegate: AutocompleteResponseDelegate = mockk()

    @Test
    fun `Given a responder When suggestions are replied Then forward them once`() = runTest {
        val sent = slot<List<String>>()
        coEvery { delegate.replyChoices(capture(sent)) } just Runs
        val responder = JdaAutocompleteResponder(delegate)

        responder.respond(listOf("es-ES", "es-419"))

        assertEquals(listOf("es-ES", "es-419"), sent.captured)
        coVerify(exactly = 1) { delegate.replyChoices(any()) }
    }

    @Test
    fun `Given an already answered responder When replying again Then reject without a second REST call`() = runTest {
        coEvery { delegate.replyChoices(any()) } just Runs
        val responder = JdaAutocompleteResponder(delegate)
        responder.respond(listOf("es-ES"))

        val error = assertFailsWith<IllegalStateException> { responder.respond(listOf("en-US")) }

        assertTrue(error.message!!.contains("already been answered"))
        coVerify(exactly = 1) { delegate.replyChoices(any()) }
    }

    @Test
    fun `Given more than 25 suggestions When replied Then send only the first 25`() = runTest {
        val sent = slot<List<String>>()
        coEvery { delegate.replyChoices(capture(sent)) } just Runs
        val responder = JdaAutocompleteResponder(delegate)

        responder.respond((1..30).map { index -> "sound-$index" })

        assertEquals(25, sent.captured.size)
        assertEquals("sound-1", sent.captured.first())
        assertEquals("sound-25", sent.captured.last())
    }

    @Test
    fun `Given no suggestions When replied Then send an empty choice list`() = runTest {
        val sent = slot<List<String>>()
        coEvery { delegate.replyChoices(capture(sent)) } just Runs
        val responder = JdaAutocompleteResponder(delegate)

        responder.respond(emptyList())

        assertEquals(emptyList<String>(), sent.captured)
    }

    @Test
    fun `Given a JDA event When suggestions are replied Then build choices with the same name and value`() = runTest {
        val choices = slot<Collection<Command.Choice>>()
        val event: CommandAutoCompleteInteractionEvent = mockk {
            every { replyChoices(capture(choices)) } returns callbackAction()
        }

        JdaAutocompleteResponder(event).respond(listOf("es-ES", "es-419"))

        assertEquals(listOf("es-ES", "es-419"), choices.captured.map { choice -> choice.name })
        assertEquals(listOf("es-ES", "es-419"), choices.captured.map { choice -> choice.asString })
    }

    @Test
    fun `Given a JDA event already answered When replying again Then issue no second REST call`() = runTest {
        val event: CommandAutoCompleteInteractionEvent = mockk {
            every { replyChoices(any<Collection<Command.Choice>>()) } returns callbackAction()
        }
        val responder = JdaAutocompleteResponder(event)
        responder.respond(listOf("es-ES"))

        assertFailsWith<IllegalStateException> { responder.respond(listOf("en-US")) }

        verify(exactly = 1) { event.replyChoices(any<Collection<Command.Choice>>()) }
    }

    private fun callbackAction(): AutoCompleteCallbackAction = mockk {
        every { queue(any(), any()) } answers {
            firstArg<Consumer<Any?>>().accept(null)
        }
    }
}
