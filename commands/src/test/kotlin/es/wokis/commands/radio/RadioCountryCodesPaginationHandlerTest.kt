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
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

class RadioCountryCodesPaginationHandlerTest {

    private val catalog: RadioCatalogPort = mockk()
    private val localization = RadioLocalizationFixture()
    private val viewFactory = RadioMessageViewFactory(localization.service)

    @Test
    fun `Given a non country code id When constructed Then reject the handler`() {
        val error = assertFailsWith<IllegalArgumentException> {
            RadioCountryCodesPaginationHandler(ComponentId.RADIO_LIST_NEXT, catalog, viewFactory)
        }

        assertTrue(error.message!!.contains("country-code previous/next"))
    }

    @Test
    fun `Given a pagination handler When inspected Then expose the routed id`() {
        assertEquals(ComponentId.RADIO_COUNTRYCODES_PREVIOUS, previousHandler().id)
        assertEquals(ComponentId.RADIO_COUNTRYCODES_NEXT, nextHandler().id)
    }

    @Test
    fun `Given more than one page When moving forward Then render the last page with both arrows`() = runTest {
        coEvery { catalog.countryCodes() } returns (1..101).map { index -> "C$index" }
        val responder = strictComponentResponder()

        nextHandler().handle(radioComponentContext(ComponentId.RADIO_COUNTRYCODES_NEXT, payload = "1"), responder)

        val view = editedView(responder)
        assertEquals(localization.englishFormat(PlayerMessageKeys.PAGINATED_EMBED_FOOTER, 2, 2), view.embed?.footer)
        val buttons = view.rows.single().buttons
        assertFalse(buttons[0].disabled)
        assertTrue(buttons[1].disabled)
        assertEquals(listOf("2", "2"), buttons.map(ButtonView::payload))
    }

    @Test
    fun `Given the last page When moving back Then render the first page and disable the previous arrow`() = runTest {
        coEvery { catalog.countryCodes() } returns (1..101).map { index -> "C$index" }
        val responder = strictComponentResponder()

        val click = radioComponentContext(ComponentId.RADIO_COUNTRYCODES_PREVIOUS, payload = "2")
        previousHandler().handle(click, responder)

        val view = editedView(responder)
        assertEquals(localization.englishFormat(PlayerMessageKeys.PAGINATED_EMBED_FOOTER, 1, 2), view.embed?.footer)
        assertTrue(view.rows.single().buttons[0].disabled)
    }

    @Test
    fun `Given a page beyond the bounds When moving forward Then clamp to the last page`() = runTest {
        coEvery { catalog.countryCodes() } returns (1..250).map { index -> "C$index" }
        val responder = strictComponentResponder()

        nextHandler().handle(radioComponentContext(ComponentId.RADIO_COUNTRYCODES_NEXT, payload = "3"), responder)

        val view = editedView(responder)
        assertEquals(localization.englishFormat(PlayerMessageKeys.PAGINATED_EMBED_FOOTER, 3, 3), view.embed?.footer)
        assertTrue(view.rows.single().buttons[1].disabled)
    }

    @Test
    fun `Given a first page previous click When handled Then clamp to the first page`() = runTest {
        coEvery { catalog.countryCodes() } returns (1..101).map { index -> "C$index" }
        val responder = strictComponentResponder()

        val click = radioComponentContext(ComponentId.RADIO_COUNTRYCODES_PREVIOUS, payload = "1")
        previousHandler().handle(click, responder)

        assertEquals(
            localization.englishFormat(PlayerMessageKeys.PAGINATED_EMBED_FOOTER, 1, 2),
            editedView(responder).embed?.footer,
        )
    }

    @Test
    fun `Given a single page When moving forward Then keep Kord's page without arrows`() = runTest {
        coEvery { catalog.countryCodes() } returns (1..100).map { index -> "C$index" }
        val responder = strictComponentResponder()

        nextHandler().handle(radioComponentContext(ComponentId.RADIO_COUNTRYCODES_NEXT, payload = "1"), responder)

        val view = editedView(responder)
        assertNull(view.embed?.footer)
        assertTrue(view.rows.isEmpty())
    }

    @Test
    fun `Given a failed lookup When clicked Then acknowledge without editing`() = runTest {
        coEvery { catalog.countryCodes() } returns null
        val responder = strictComponentResponder()

        nextHandler().handle(radioComponentContext(ComponentId.RADIO_COUNTRYCODES_NEXT, payload = "1"), responder)

        coVerify(exactly = 1) { responder.deferEdit() }
        coVerify(exactly = 0) { responder.edit(any()) }
    }

    @Test
    fun `Given an empty lookup When clicked Then acknowledge without editing`() = runTest {
        coEvery { catalog.countryCodes() } returns emptyList()
        val responder = strictComponentResponder()

        val click = radioComponentContext(ComponentId.RADIO_COUNTRYCODES_PREVIOUS, payload = "2")
        previousHandler().handle(click, responder)

        coVerify(exactly = 1) { responder.deferEdit() }
        coVerify(exactly = 0) { responder.edit(any()) }
    }

    @Test
    fun `Given a pagination click When handled Then acknowledge by edit before editing the page`() = runTest {
        coEvery { catalog.countryCodes() } returns (1..101).map { index -> "C$index" }
        val responder = strictComponentResponder()

        nextHandler().handle(radioComponentContext(ComponentId.RADIO_COUNTRYCODES_NEXT, payload = "1"), responder)

        coVerifyOrder {
            responder.deferEdit()
            responder.edit(any())
        }
    }

    private fun nextHandler() = RadioCountryCodesPaginationHandler(
        ComponentId.RADIO_COUNTRYCODES_NEXT,
        catalog,
        viewFactory,
    )

    private fun previousHandler() =
        RadioCountryCodesPaginationHandler(ComponentId.RADIO_COUNTRYCODES_PREVIOUS, catalog, viewFactory)
}
