package es.wokis.commands.queue

import es.wokis.commands.neutral.CommandResponder
import es.wokis.commands.player.PlayerActionsPort
import es.wokis.commands.player.PlayerLocalizationFixture
import es.wokis.commands.player.QueueMessageViewFactory
import es.wokis.commands.player.RecordingCommandResponder
import es.wokis.commands.player.commandContext
import es.wokis.commands.player.playerActor
import es.wokis.commands.player.queueSnapshot
import es.wokis.commands.player.testTrack
import es.wokis.localization.PlayerMessageKeys
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

class QueueCommandHandlerTest {

    private val actions: PlayerActionsPort = mockk()
    private val localization = PlayerLocalizationFixture()
    private val viewFactory = QueueMessageViewFactory(localization.service)
    private val handler = QueueCommandHandler(actions, localization.service, viewFactory)

    @Test
    fun `Given the queue handler When inspected Then it owns Kord's command name`() {
        assertEquals("queue", handler.name)
    }

    @Test
    fun `Given a text only responder When queue runs Then fail before the voice guard`() = runTest {
        val responder: CommandResponder = mockk()

        val error = assertFailsWith<IllegalStateException> { handler.handle(commandContext(), responder) }

        assertTrue(error.message!!.contains("view-capable responder"))
        coVerify(exactly = 0) { actions.isVoiceAvailable(any()) }
    }

    @Test
    fun `Given a direct message When queue runs Then reply no guild as text`() = runTest {
        val responder = RecordingCommandResponder()

        handler.handle(commandContext(guildId = null), responder)

        assertEquals(
            listOf(localization.english(PlayerMessageKeys.ERROR_NO_GUILD)),
            responder.responses.map { it.content },
        )
        assertTrue(responder.views.isEmpty())
        coVerify(exactly = 0) { actions.isVoiceAvailable(any()) }
    }

    @Test
    fun `Given a caller outside voice When queue runs Then reply no voice channel as text`() = runTest {
        val responder = RecordingCommandResponder()
        coEvery { actions.isVoiceAvailable(playerActor()) } returns false

        handler.handle(commandContext(), responder)

        assertEquals(
            listOf(localization.english(PlayerMessageKeys.ERROR_NO_VOICE_CHANNEL)),
            responder.responses.map { it.content },
        )
        assertTrue(responder.views.isEmpty())
        coVerify(exactly = 0) { actions.snapshot(any()) }
    }

    @Test
    fun `Given an unreachable guild snapshot When queue runs Then reply no guild as text`() = runTest {
        val responder = RecordingCommandResponder()
        coEvery { actions.isVoiceAvailable(playerActor()) } returns true
        coEvery { actions.snapshot(playerActor()) } returns null

        handler.handle(commandContext(), responder)

        assertEquals(
            listOf(localization.english(PlayerMessageKeys.ERROR_NO_GUILD)),
            responder.responses.map { it.content },
        )
        assertTrue(responder.views.isEmpty())
    }

    @Test
    fun `Given a multi page queue When queue runs Then render the first page with pagination buttons`() = runTest {
        val responder = RecordingCommandResponder()
        val tracks = listOf(testTrack("a".repeat(400)), testTrack("b".repeat(400)), testTrack("c".repeat(400)))
        coEvery { actions.isVoiceAvailable(playerActor()) } returns true
        coEvery { actions.snapshot(playerActor()) } returns queueSnapshot(tracks = tracks)

        handler.handle(commandContext(), responder)

        val view = responder.views.single()
        val embed = requireNotNull(view.embed)
        assertEquals(localization.english(PlayerMessageKeys.QUEUE_EMBED_TITLE), embed.title)
        assertEquals(
            localization.englishFormat(PlayerMessageKeys.QUEUE_EMBED_DESCRIPTION, 3, "Guild"),
            embed.description,
        )
        val field = embed.fields.single()
        assertEquals("\u2800", field.name)
        assertTrue(field.inline)
        assertEquals("${tracks[0].displayName}, ${tracks[1].displayName}", field.value)
        assertEquals(localization.englishFormat(PlayerMessageKeys.PAGINATED_EMBED_FOOTER, 1, 2), embed.footer)
        val buttons = view.rows.single().buttons
        assertEquals(2, buttons.size)
        assertTrue(buttons[0].disabled, "the first page disables the previous button")
        assertFalse(buttons[1].disabled, "the first page keeps the next button enabled")
        assertTrue(responder.responses.isEmpty())
    }

    @Test
    fun `Given a single page queue When queue runs Then render it without pagination buttons`() = runTest {
        val responder = RecordingCommandResponder()
        coEvery { actions.isVoiceAvailable(playerActor()) } returns true
        coEvery { actions.snapshot(playerActor()) } returns queueSnapshot(tracks = listOf(testTrack("only")))

        handler.handle(commandContext(), responder)

        val view = responder.views.single()
        val embed = requireNotNull(view.embed)
        assertEquals("only", embed.fields.single().value)
        assertEquals(localization.englishFormat(PlayerMessageKeys.PAGINATED_EMBED_FOOTER, 1, 1), embed.footer)
        assertTrue(view.rows.isEmpty(), "a single page must not paginate")
    }

    @Test
    fun `Given an empty queue When queue runs Then render an empty queue without footer or buttons`() = runTest {
        val responder = RecordingCommandResponder()
        coEvery { actions.isVoiceAvailable(playerActor()) } returns true
        coEvery { actions.snapshot(playerActor()) } returns queueSnapshot()

        handler.handle(commandContext(), responder)

        val view = responder.views.single()
        val embed = requireNotNull(view.embed)
        assertTrue(embed.fields.isEmpty())
        assertNull(embed.footer)
        assertTrue(view.rows.isEmpty())
    }
}
