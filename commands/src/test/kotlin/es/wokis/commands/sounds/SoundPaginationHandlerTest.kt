package es.wokis.commands.sounds

import es.wokis.commands.neutral.ComponentContext
import es.wokis.commands.neutral.ComponentResponder
import es.wokis.commands.player.PlayerLocalizationFixture
import es.wokis.commands.sound.SoundsCatalogPort
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
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

class SoundPaginationHandlerTest {

    private val soundsCatalog: SoundsCatalogPort = mockk()
    private val localization = PlayerLocalizationFixture()
    private val viewFactory = SoundsMessageViewFactory(localization.service)

    @Test
    fun `Given a next click on the first page When handled Then edit the message with the second page`() = runTest {
        givenTwoPages()
        val responder = strictComponentResponder()

        nextHandler().handle(context(ComponentId.SOUNDS_NEXT, payload = "1"), responder)

        val view = editOf(responder)
        assertEquals(localization.englishFormat(PlayerMessageKeys.PAGINATED_EMBED_FOOTER, 2, 2), view.embed?.footer)
        assertEquals("d".repeat(600), view.embed?.fields?.first()?.value)
    }

    @Test
    fun `Given a previous click on the second page When handled Then edit the message with the first page`() = runTest {
        givenTwoPages()
        val responder = strictComponentResponder()

        previousHandler().handle(context(ComponentId.SOUNDS_PREVIOUS, payload = "2"), responder)

        val view = editOf(responder)
        assertEquals(localization.englishFormat(PlayerMessageKeys.PAGINATED_EMBED_FOOTER, 1, 2), view.embed?.footer)
        assertTrue(view.rows.single().buttons[0].disabled, "the first page disables the previous button")
    }

    @Test
    fun `Given a click without a payload When handled Then start from the first page and move one step`() = runTest {
        givenTwoPages()
        val responder = strictComponentResponder()

        nextHandler().handle(context(ComponentId.SOUNDS_NEXT, payload = null), responder)

        assertEquals(
            localization.englishFormat(PlayerMessageKeys.PAGINATED_EMBED_FOOTER, 2, 2),
            editOf(responder).embed?.footer,
        )
    }

    @Test
    fun `Given a malformed payload When handled Then fall back to the first page before stepping`() = runTest {
        givenTwoPages()
        val responder = strictComponentResponder()

        nextHandler().handle(context(ComponentId.SOUNDS_NEXT, payload = "not-a-page"), responder)

        assertEquals(
            localization.englishFormat(PlayerMessageKeys.PAGINATED_EMBED_FOOTER, 2, 2),
            editOf(responder).embed?.footer,
        )
    }

    @Test
    fun `Given an out of bounds payload When moving forward Then clamp to the last page`() = runTest {
        givenTwoPages()
        val responder = strictComponentResponder()

        nextHandler().handle(context(ComponentId.SOUNDS_NEXT, payload = "99"), responder)

        val view = editOf(responder)
        assertEquals(localization.englishFormat(PlayerMessageKeys.PAGINATED_EMBED_FOOTER, 2, 2), view.embed?.footer)
        assertTrue(view.rows.single().buttons[1].disabled, "the last page disables the next button")
    }

    @Test
    fun `Given a first page previous click When handled Then clamp and keep the previous button disabled`() = runTest {
        givenTwoPages()
        val responder = strictComponentResponder()

        previousHandler().handle(context(ComponentId.SOUNDS_PREVIOUS, payload = "1"), responder)

        val view = editOf(responder)
        assertEquals(localization.englishFormat(PlayerMessageKeys.PAGINATED_EMBED_FOOTER, 1, 2), view.embed?.footer)
        assertTrue(view.rows.single().buttons[0].disabled)
        assertFalse(view.rows.single().buttons[1].disabled)
    }

    @Test
    fun `Given a pagination click When handled Then acknowledge by edit before editing the page`() = runTest {
        givenTwoPages()
        val responder = strictComponentResponder()

        nextHandler().handle(context(ComponentId.SOUNDS_NEXT, payload = "1"), responder)

        coVerifyOrder {
            responder.deferEdit()
            responder.edit(any())
        }
    }

    @Test
    fun `Given an empty catalog When handled Then acknowledge and still edit the empty page`() = runTest {
        coEvery { soundsCatalog.soundNames() } returns emptyList()
        val responder = strictComponentResponder()

        nextHandler().handle(context(ComponentId.SOUNDS_NEXT, payload = "1"), responder)

        val view = editOf(responder)
        assertTrue(view.embed!!.fields.isEmpty())
        assertNull(view.embed?.footer)
    }

    @Test
    fun `Given a direct message click When handled Then edit the catalog page because sounds are not guild scoped`() = runTest {
        givenTwoPages()
        val responder = strictComponentResponder()

        nextHandler().handle(context(ComponentId.SOUNDS_NEXT, payload = "1").copy(guildId = null), responder)

        coVerify(exactly = 1) { responder.deferEdit() }
        coVerify(exactly = 1) { responder.edit(any()) }
    }

    @Test
    fun `Given a non pagination id When constructed Then reject the handler`() {
        val error = assertFailsWith<IllegalArgumentException> {
            SoundPaginationHandler(ComponentId.PLAYER_PAUSE, soundsCatalog, viewFactory)
        }

        assertTrue(error.message!!.contains("sounds previous/next"))
    }

    @Test
    fun `Given a pagination handler When inspected Then expose the routed id`() {
        assertEquals(ComponentId.SOUNDS_PREVIOUS, previousHandler().id)
        assertEquals(ComponentId.SOUNDS_NEXT, nextHandler().id)
    }

    private fun nextHandler() = SoundPaginationHandler(ComponentId.SOUNDS_NEXT, soundsCatalog, viewFactory)

    private fun previousHandler() = SoundPaginationHandler(ComponentId.SOUNDS_PREVIOUS, soundsCatalog, viewFactory)

    private fun givenTwoPages() {
        coEvery { soundsCatalog.soundNames() } returns listOf("a", "b", "c", "d").map { letter -> letter.repeat(600) }
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

    private fun context(componentId: ComponentId, payload: String?): ComponentContext = ComponentContext(
        componentId = componentId,
        guildId = GuildId(42uL),
        channelId = ChannelId(43uL),
        userId = UserId(44uL),
        guildLocale = null,
        payload = payload,
    )
}
