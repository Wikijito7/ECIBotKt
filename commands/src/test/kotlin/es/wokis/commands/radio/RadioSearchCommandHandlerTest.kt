package es.wokis.commands.radio

import es.wokis.commands.neutral.CommandResponder
import es.wokis.commands.player.RecordingCommandResponder
import es.wokis.localization.PlayerMessageKeys
import es.wokis.localization.RadioMessageKeys
import es.wokis.message.ButtonView
import es.wokis.message.ComponentId
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

class RadioSearchCommandHandlerTest {

    private val catalog: RadioCatalogPort = mockk()
    private val localization = RadioLocalizationFixture()
    private val viewFactory = RadioMessageViewFactory(localization.service)

    @Test
    fun `Given each search kind When inspected Then the handler owns Kord's option name`() {
        assertEquals("name", nameHandler().name)
        assertEquals("countrycode", countryCodeHandler().name)
    }

    @Test
    fun `Given a text only responder When a search runs Then fail before reading the catalog`() = runTest {
        val responder: CommandResponder = mockk()

        val error = assertFailsWith<IllegalStateException> { nameHandler().handle(radioContext(), responder) }

        assertTrue(error.message!!.contains("view-capable responder"))
        coVerify(exactly = 0) { catalog.searchByName(any(), any()) }
    }

    @Test
    fun `Given a name search When it runs Then query the catalog with the typed name and carry it on the arrows`() =
        runTest {
            coEvery { catalog.searchByName("Rock", 1) } returns radioPage(
                currentPage = 1,
                maxPage = 2,
                stations = (1..6).map { index -> radioStation("rock-$index") },
            )
            val responder = RecordingCommandResponder()

            nameHandler().handle(radioContext(options = searchOptions("name", "Rock")), responder)

            val view = responder.views.single()
            val embed = requireNotNull(view.embed)
            assertEquals(localization.english(RadioMessageKeys.RADIO_LIST_EMBED_TITLE), embed.title)
            assertEquals(3, embed.fields.size)
            assertEquals(localization.englishFormat(PlayerMessageKeys.PAGINATED_EMBED_FOOTER, 1, 2), embed.footer)
            val buttons = view.rows.single().buttons
            assertEquals(
                listOf(ComponentId.RADIO_SEARCH_NAME_PREVIOUS, ComponentId.RADIO_SEARCH_NAME_NEXT),
                buttons.map(ButtonView::componentId),
            )
            assertEquals(listOf("1::Rock", "1::Rock"), buttons.map(ButtonView::payload))
            coVerify(exactly = 1) { catalog.searchByName("Rock", 1) }
            coVerify(exactly = 0) { catalog.searchByCountryCode(any(), any()) }
        }

    @Test
    fun `Given a country code search When it runs Then query the catalog by country and carry it on the arrows`() = runTest {
        coEvery { catalog.searchByCountryCode("ES", 1) } returns radioPage(
            currentPage = 1,
            maxPage = 2,
            stations = listOf(radioStation("Rock FM")),
        )
        val responder = RecordingCommandResponder()

        countryCodeHandler().handle(radioContext(options = searchOptions("countrycode", "ES")), responder)

        val view = responder.views.single()
        val buttons = view.rows.single().buttons
        assertEquals(
            listOf(ComponentId.RADIO_SEARCH_COUNTRY_CODE_PREVIOUS, ComponentId.RADIO_SEARCH_COUNTRY_CODE_NEXT),
            buttons.map(ButtonView::componentId),
        )
        assertEquals(listOf("1::ES", "1::ES"), buttons.map(ButtonView::payload))
        coVerify(exactly = 1) { catalog.searchByCountryCode("ES", 1) }
        coVerify(exactly = 0) { catalog.searchByName(any(), any()) }
    }

    @Test
    fun `Given a missing query option When a search runs Then search the empty term and omit the query payload`() =
        runTest {
            coEvery { catalog.searchByName("", 1) } returns radioPage(
                currentPage = 1,
                maxPage = 2,
                stations = listOf(radioStation("Rock FM")),
            )
            val responder = RecordingCommandResponder()

            nameHandler().handle(radioContext(), responder)

            assertEquals(
                listOf("1", "1"),
                responder.views.single().rows.single().buttons.map(ButtonView::payload),
            )
        }

    @Test
    fun `Given a failed search lookup When it runs Then render Kord's empty first page without arrows`() = runTest {
        coEvery { catalog.searchByName("Rock", 1) } returns null
        val responder = RecordingCommandResponder()

        nameHandler().handle(radioContext(options = searchOptions("name", "Rock")), responder)

        val view = responder.views.single()
        assertTrue(requireNotNull(view.embed).fields.isEmpty())
        assertEquals(localization.englishFormat(PlayerMessageKeys.PAGINATED_EMBED_FOOTER, 1, 1), view.embed?.footer)
        assertTrue(view.rows.isEmpty())
    }

    private fun nameHandler() = RadioSearchCommandHandler(RadioSearchKind.NAME, catalog, viewFactory)

    private fun countryCodeHandler() = RadioSearchCommandHandler(RadioSearchKind.COUNTRY_CODE, catalog, viewFactory)

    private fun searchOptions(optionName: String, query: String): Map<String, String> = mapOf(optionName to query)
}
