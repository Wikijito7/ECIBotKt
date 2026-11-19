package es.wokis.commands.radio

import es.wokis.localization.PlayerMessageKeys
import es.wokis.message.ButtonView
import es.wokis.message.ComponentId
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

class RadioListPaginationHandlerTest {

    private val catalog: RadioCatalogPort = mockk()
    private val localization = RadioLocalizationFixture()
    private val viewFactory = RadioMessageViewFactory(localization.service)

    @Test
    fun `Given a non pagination id When constructed Then reject the handler`() {
        val error = assertFailsWith<IllegalArgumentException> {
            RadioListPaginationHandler(ComponentId.RADIO_SEARCH_NAME_NEXT, catalog, viewFactory)
        }

        assertTrue(error.message!!.contains("radio list previous/next"))
    }

    @Test
    fun `Given a pagination handler When inspected Then expose the routed id`() {
        assertEquals(ComponentId.RADIO_LIST_PREVIOUS, previousHandler().id)
        assertEquals(ComponentId.RADIO_LIST_NEXT, nextHandler().id)
    }

    @Test
    fun `Given a next click on the first page When handled Then look up the second page and disable the next arrow`() =
        runTest {
            givenTwoPages()
            val responder = strictComponentResponder()

            nextHandler().handle(radioComponentContext(ComponentId.RADIO_LIST_NEXT, payload = "1"), responder)

            coVerify(exactly = 1) { catalog.stationPage(2) }
            val view = editedView(responder)
            assertEquals(localization.englishFormat(PlayerMessageKeys.PAGINATED_EMBED_FOOTER, 2, 2), view.embed?.footer)
            val buttons = view.rows.single().buttons
            assertFalse(buttons[0].disabled)
            assertTrue(buttons[1].disabled)
            assertEquals(listOf("2", "2"), buttons.map(ButtonView::payload))
        }

    @Test
    fun `Given a previous click on the second page When handled Then look up the first page and disable the previous arrow`() =
        runTest {
            givenTwoPages()
            val responder = strictComponentResponder()

            previousHandler().handle(radioComponentContext(ComponentId.RADIO_LIST_PREVIOUS, payload = "2"), responder)

            coVerify(exactly = 1) { catalog.stationPage(1) }
            val view = editedView(responder)
            assertEquals(localization.englishFormat(PlayerMessageKeys.PAGINATED_EMBED_FOOTER, 1, 2), view.embed?.footer)
            assertTrue(view.rows.single().buttons[0].disabled)
        }

    @Test
    fun `Given a missing or malformed payload When moving forward Then start from the first page and step once`() = runTest {
        givenTwoPages()

        listOf(null, "", "not-a-page").forEach { payload ->
            val context = radioComponentContext(ComponentId.RADIO_LIST_NEXT, payload = payload)
            nextHandler().handle(context, strictComponentResponder())
        }

        coVerify(exactly = 3) { catalog.stationPage(2) }
        coVerify(exactly = 0) { catalog.stationPage(1) }
    }

    @Test
    fun `Given a first page previous click When handled Then clamp and keep the page bounds`() = runTest {
        givenTwoPages()
        val responder = strictComponentResponder()

        previousHandler().handle(radioComponentContext(ComponentId.RADIO_LIST_PREVIOUS, payload = "1"), responder)

        coVerify(exactly = 1) { catalog.stationPage(1) }
        assertTrue(editedView(responder).rows.single().buttons[0].disabled)
    }

    @Test
    fun `Given a failed lookup When handled Then acknowledge and edit Kord's empty first page`() = runTest {
        coEvery { catalog.stationPage(any()) } returns null
        val responder = strictComponentResponder()

        nextHandler().handle(radioComponentContext(ComponentId.RADIO_LIST_NEXT, payload = "3"), responder)

        val view = editedView(responder)
        assertTrue(requireNotNull(view.embed).fields.isEmpty())
        assertEquals(localization.englishFormat(PlayerMessageKeys.PAGINATED_EMBED_FOOTER, 1, 1), view.embed?.footer)
        assertTrue(view.rows.isEmpty())
    }

    @Test
    fun `Given a pagination click When handled Then acknowledge by edit before editing the page`() = runTest {
        givenTwoPages()
        val responder = strictComponentResponder()

        nextHandler().handle(radioComponentContext(ComponentId.RADIO_LIST_NEXT, payload = "1"), responder)

        coVerifyOrder {
            responder.deferEdit()
            responder.edit(any())
        }
    }

    private fun nextHandler() = RadioListPaginationHandler(ComponentId.RADIO_LIST_NEXT, catalog, viewFactory)

    private fun previousHandler() = RadioListPaginationHandler(ComponentId.RADIO_LIST_PREVIOUS, catalog, viewFactory)

    private fun givenTwoPages() {
        coEvery { catalog.stationPage(1) } returns radioPage(
            currentPage = 1,
            maxPage = 2,
            stations = listOf(radioStation("station-1")),
        )
        coEvery { catalog.stationPage(2) } returns radioPage(
            currentPage = 2,
            maxPage = 2,
            stations = listOf(radioStation("station-2")),
        )
    }
}
