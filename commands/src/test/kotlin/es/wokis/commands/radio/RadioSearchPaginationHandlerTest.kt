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

class RadioSearchPaginationHandlerTest {

    private val catalog: RadioCatalogPort = mockk()
    private val localization = RadioLocalizationFixture()
    private val viewFactory = RadioMessageViewFactory(localization.service)

    @Test
    fun `Given an id of the other search kind When constructed Then reject the handler`() {
        val error = assertFailsWith<IllegalArgumentException> {
            RadioSearchPaginationHandler(
                ComponentId.RADIO_SEARCH_COUNTRY_CODE_NEXT,
                RadioSearchKind.NAME,
                catalog,
                viewFactory,
            )
        }

        assertTrue(error.message!!.contains("own search kind"))
    }

    @Test
    fun `Given a search pagination handler When inspected Then expose the routed id`() {
        assertEquals(ComponentId.RADIO_SEARCH_NAME_PREVIOUS, namePreviousHandler().id)
        assertEquals(ComponentId.RADIO_SEARCH_NAME_NEXT, nameNextHandler().id)
        assertEquals(ComponentId.RADIO_SEARCH_COUNTRY_CODE_NEXT, countryCodeNextHandler().id)
    }

    @Test
    fun `Given a name search next click When handled Then repeat the same query on the next page`() = runTest {
        coEvery { catalog.searchByName("Rock", 2) } returns radioPage(
            currentPage = 2,
            maxPage = 2,
            stations = listOf(radioStation("rock-2")),
        )
        val responder = strictComponentResponder()

        nameNextHandler().handle(
            radioComponentContext(ComponentId.RADIO_SEARCH_NAME_NEXT, payload = "1::Rock"),
            responder,
        )

        coVerify(exactly = 1) { catalog.searchByName("Rock", 2) }
        val view = editedView(responder)
        assertEquals(localization.englishFormat(PlayerMessageKeys.PAGINATED_EMBED_FOOTER, 2, 2), view.embed?.footer)
        val buttons = view.rows.single().buttons
        assertEquals(
            listOf(ComponentId.RADIO_SEARCH_NAME_PREVIOUS, ComponentId.RADIO_SEARCH_NAME_NEXT),
            buttons.map(ButtonView::componentId),
        )
        assertEquals(listOf("2::Rock", "2::Rock"), buttons.map(ButtonView::payload))
        assertFalse(buttons[0].disabled)
        assertTrue(buttons[1].disabled)
    }

    @Test
    fun `Given a name search previous click When handled Then repeat the same query on the previous page`() = runTest {
        coEvery { catalog.searchByName("Rock", 1) } returns radioPage(
            currentPage = 1,
            maxPage = 2,
            stations = listOf(radioStation("rock-1")),
        )
        val responder = strictComponentResponder()

        namePreviousHandler().handle(
            radioComponentContext(ComponentId.RADIO_SEARCH_NAME_PREVIOUS, payload = "2::Rock"),
            responder,
        )

        coVerify(exactly = 1) { catalog.searchByName("Rock", 1) }
        assertTrue(editedView(responder).rows.single().buttons[0].disabled)
    }

    @Test
    fun `Given a country code search next click When handled Then repeat the country lookup with its own arrows`() = runTest {
        coEvery { catalog.searchByCountryCode("ES", 2) } returns radioPage(
            currentPage = 2,
            maxPage = 2,
            stations = listOf(radioStation("es-2")),
        )
        val responder = strictComponentResponder()

        countryCodeNextHandler().handle(
            radioComponentContext(ComponentId.RADIO_SEARCH_COUNTRY_CODE_NEXT, payload = "1::ES"),
            responder,
        )

        coVerify(exactly = 1) { catalog.searchByCountryCode("ES", 2) }
        assertEquals(
            listOf(ComponentId.RADIO_SEARCH_COUNTRY_CODE_PREVIOUS, ComponentId.RADIO_SEARCH_COUNTRY_CODE_NEXT),
            editedView(responder).rows.single().buttons.map(ButtonView::componentId),
        )
    }

    @Test
    fun `Given an old payload without a query When clicked Then acknowledge without editing or searching`() = runTest {
        val withoutPayload = strictComponentResponder()
        val emptyPayload = strictComponentResponder()

        nameNextHandler().handle(
            radioComponentContext(ComponentId.RADIO_SEARCH_NAME_NEXT, payload = "1"),
            withoutPayload,
        )
        nameNextHandler().handle(
            radioComponentContext(ComponentId.RADIO_SEARCH_NAME_NEXT, payload = null),
            emptyPayload,
        )

        listOf(withoutPayload, emptyPayload).forEach { responder ->
            coVerify(exactly = 1) { responder.deferEdit() }
            coVerify(exactly = 0) { responder.edit(any()) }
        }
        coVerify(exactly = 0) { catalog.searchByName(any(), any()) }
    }

    @Test
    fun `Given a query containing the separator When clicked Then keep it intact`() = runTest {
        coEvery { catalog.searchByName("Rock::Live", 2) } returns radioPage(
            currentPage = 2,
            maxPage = 2,
            stations = listOf(radioStation("rock-live")),
        )
        val responder = strictComponentResponder()

        nameNextHandler().handle(
            radioComponentContext(ComponentId.RADIO_SEARCH_NAME_NEXT, payload = "1::Rock::Live"),
            responder,
        )

        coVerify(exactly = 1) { catalog.searchByName("Rock::Live", 2) }
        assertEquals(
            listOf("2::Rock::Live", "2::Rock::Live"),
            editedView(responder).rows.single().buttons.map(ButtonView::payload),
        )
    }

    @Test
    fun `Given a failed search page When clicked Then edit Kord's empty first page`() = runTest {
        coEvery { catalog.searchByName("Rock", 2) } returns null
        val responder = strictComponentResponder()

        nameNextHandler().handle(
            radioComponentContext(ComponentId.RADIO_SEARCH_NAME_NEXT, payload = "1::Rock"),
            responder,
        )

        val view = editedView(responder)
        assertTrue(requireNotNull(view.embed).fields.isEmpty())
        assertEquals(localization.englishFormat(PlayerMessageKeys.PAGINATED_EMBED_FOOTER, 1, 1), view.embed?.footer)
        assertTrue(view.rows.isEmpty())
    }

    @Test
    fun `Given a search pagination click When handled Then acknowledge by edit before editing the page`() = runTest {
        coEvery { catalog.searchByName("Rock", 2) } returns radioPage(
            currentPage = 2,
            maxPage = 2,
            stations = listOf(radioStation("rock-2")),
        )
        val responder = strictComponentResponder()

        nameNextHandler().handle(
            radioComponentContext(ComponentId.RADIO_SEARCH_NAME_NEXT, payload = "1::Rock"),
            responder,
        )

        coVerifyOrder {
            responder.deferEdit()
            responder.edit(any())
        }
    }

    private fun nameNextHandler() =
        RadioSearchPaginationHandler(ComponentId.RADIO_SEARCH_NAME_NEXT, RadioSearchKind.NAME, catalog, viewFactory)

    private fun namePreviousHandler() =
        RadioSearchPaginationHandler(ComponentId.RADIO_SEARCH_NAME_PREVIOUS, RadioSearchKind.NAME, catalog, viewFactory)

    private fun countryCodeNextHandler() = RadioSearchPaginationHandler(
        ComponentId.RADIO_SEARCH_COUNTRY_CODE_NEXT,
        RadioSearchKind.COUNTRY_CODE,
        catalog,
        viewFactory,
    )
}
