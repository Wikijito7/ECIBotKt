package es.wokis.commands.queue

import es.wokis.commands.neutral.ComponentContext
import es.wokis.commands.neutral.ComponentResponder
import es.wokis.commands.player.PlayerActionsPort
import es.wokis.commands.player.PlayerLocalizationFixture
import es.wokis.commands.player.QueueMessageViewFactory
import es.wokis.commands.player.playerActor
import es.wokis.commands.player.queueSnapshot
import es.wokis.commands.player.testTrack
import es.wokis.domain.model.ChannelId
import es.wokis.domain.model.GuildId
import es.wokis.domain.model.UserId
import es.wokis.localization.PlayerMessageKeys
import es.wokis.message.ComponentId
import es.wokis.message.MessageView
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

class QueuePaginationHandlerTest {

    private val actions: PlayerActionsPort = mockk()
    private val localization = PlayerLocalizationFixture()
    private val viewFactory = QueueMessageViewFactory(localization.service)

    @Test
    fun `Given a next click on the first page When handled Then edit the message with the second page`() = runTest {
        val responder = strictComponentResponder()
        everySnapshotIsMultiPage()
        val handler = QueuePaginationHandler(ComponentId.QUEUE_NEXT, actions, viewFactory)

        handler.handle(context(ComponentId.QUEUE_NEXT, payload = "1"), responder)

        val view = editOf(responder)
        assertEquals(footer(2, 5), view.embed?.footer)
        assertEquals(listOf(trackName(2)), view.embed?.fields?.map { it.value })
    }

    @Test
    fun `Given a previous click on the second page When handled Then edit the message with the first page`() = runTest {
        val responder = strictComponentResponder()
        everySnapshotIsMultiPage()
        val handler = QueuePaginationHandler(ComponentId.QUEUE_PREVIOUS, actions, viewFactory)

        handler.handle(context(ComponentId.QUEUE_PREVIOUS, payload = "2"), responder)

        val view = editOf(responder)
        assertEquals(footer(1, 5), view.embed?.footer)
        assertTrue(view.rows.single().buttons[0].disabled, "the first page disables the previous button")
    }

    @Test
    fun `Given a click without a payload When handled Then start from the first page and move one step`() = runTest {
        val responder = strictComponentResponder()
        everySnapshotIsMultiPage()
        val handler = QueuePaginationHandler(ComponentId.QUEUE_NEXT, actions, viewFactory)

        handler.handle(context(ComponentId.QUEUE_NEXT, payload = null), responder)

        assertEquals(footer(2, 5), editOf(responder).embed?.footer)
    }

    @Test
    fun `Given a malformed payload When handled Then fall back to the first page before stepping`() = runTest {
        val responder = strictComponentResponder()
        everySnapshotIsMultiPage()
        val handler = QueuePaginationHandler(ComponentId.QUEUE_NEXT, actions, viewFactory)

        handler.handle(context(ComponentId.QUEUE_NEXT, payload = "not-a-page"), responder)

        assertEquals(footer(2, 5), editOf(responder).embed?.footer)
    }

    @Test
    fun `Given an out of bounds payload When moving forward Then clamp to the last page`() = runTest {
        val responder = strictComponentResponder()
        everySnapshotIsMultiPage()
        val handler = QueuePaginationHandler(ComponentId.QUEUE_NEXT, actions, viewFactory)

        handler.handle(context(ComponentId.QUEUE_NEXT, payload = "99"), responder)

        val view = editOf(responder)
        assertEquals(footer(5, 5), view.embed?.footer)
        assertTrue(view.rows.single().buttons[1].disabled, "the last page disables the next button")
    }

    @Test
    fun `Given a first page previous click When handled Then clamp and keep the previous button disabled`() = runTest {
        val responder = strictComponentResponder()
        everySnapshotIsMultiPage()
        val handler = QueuePaginationHandler(ComponentId.QUEUE_PREVIOUS, actions, viewFactory)

        handler.handle(context(ComponentId.QUEUE_PREVIOUS, payload = "1"), responder)

        val view = editOf(responder)
        assertEquals(footer(1, 5), view.embed?.footer)
        assertTrue(view.rows.single().buttons[0].disabled)
        assertFalse(view.rows.single().buttons[1].disabled)
    }

    @Test
    fun `Given a pagination click When handled Then acknowledge by edit before editing the page`() = runTest {
        val responder = strictComponentResponder()
        everySnapshotIsMultiPage()
        val handler = QueuePaginationHandler(ComponentId.QUEUE_NEXT, actions, viewFactory)

        handler.handle(context(ComponentId.QUEUE_NEXT, payload = "1"), responder)

        coVerifyOrder {
            responder.deferEdit()
            responder.edit(any())
        }
    }

    @Test
    fun `Given a direct message click When handled Then acknowledge and stop without an edit`() = runTest {
        val responder = strictComponentResponder()
        val handler = QueuePaginationHandler(ComponentId.QUEUE_NEXT, actions, viewFactory)

        handler.handle(context(ComponentId.QUEUE_NEXT, payload = "1").copy(guildId = null), responder)

        coVerify(exactly = 1) { responder.deferEdit() }
        coVerify(exactly = 0) { responder.edit(any()) }
        coVerify(exactly = 0) { actions.snapshot(any()) }
    }

    @Test
    fun `Given an unreachable guild snapshot When handled Then acknowledge and stop without an edit`() = runTest {
        val responder = strictComponentResponder()
        coEvery { actions.snapshot(any()) } returns null
        val handler = QueuePaginationHandler(ComponentId.QUEUE_NEXT, actions, viewFactory)

        handler.handle(context(ComponentId.QUEUE_NEXT, payload = "1"), responder)

        coVerify(exactly = 1) { responder.deferEdit() }
        coVerify(exactly = 0) { responder.edit(any()) }
    }

    @Test
    fun `Given a non pagination id When constructed Then reject the handler`() {
        val error = assertFailsWith<IllegalArgumentException> {
            QueuePaginationHandler(ComponentId.PLAYER_PAUSE, actions, viewFactory)
        }

        assertTrue(error.message!!.contains("queue previous/next"))
    }

    @Test
    fun `Given a pagination handler When inspected Then expose the routed id`() {
        val previous = QueuePaginationHandler(ComponentId.QUEUE_PREVIOUS, actions, viewFactory)
        val next = QueuePaginationHandler(ComponentId.QUEUE_NEXT, actions, viewFactory)

        assertEquals(ComponentId.QUEUE_PREVIOUS, previous.id)
        assertEquals(ComponentId.QUEUE_NEXT, next.id)
    }

    private fun strictComponentResponder(): ComponentResponder = mockk<ComponentResponder>().also { responder ->
        coJustRun { responder.deferEdit() }
        coJustRun { responder.edit(any()) }
    }

    private fun editOf(responder: ComponentResponder): MessageView {
        val edited = slot<MessageView>()
        coVerify(exactly = 1) { responder.edit(capture(edited)) }
        return edited.captured
    }

    private fun everySnapshotIsMultiPage() {
        // Each 600 character name fills its own page, so five tracks render five pages.
        coEvery { actions.snapshot(playerActor()) } returns queueSnapshot(
            tracks = (1..5).map { index -> testTrack(trackName(index)) },
        )
    }

    private fun trackName(index: Int): String = "track-$index-" + "x".repeat(590)

    private fun footer(page: Int, pageCount: Int): String =
        localization.englishFormat(PlayerMessageKeys.PAGINATED_EMBED_FOOTER, page, pageCount)

    private fun context(componentId: ComponentId, payload: String?): ComponentContext = ComponentContext(
        componentId = componentId,
        guildId = GuildId(42uL),
        channelId = ChannelId(43uL),
        userId = UserId(44uL),
        guildLocale = null,
        payload = payload,
    )
}
