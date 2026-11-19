package es.wokis.commands.radio

import es.wokis.commands.neutral.CommandResponder
import es.wokis.commands.player.RecordingCommandResponder
import es.wokis.domain.locale.LocaleCode
import es.wokis.localization.PlayerMessageKeys
import es.wokis.localization.RadioMessageKeys
import es.wokis.message.ButtonView
import es.wokis.message.ComponentId
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

class RadioListCommandHandlerTest {

    private val catalog: RadioCatalogPort = mockk()
    private val localization = RadioLocalizationFixture()
    private val viewFactory = RadioMessageViewFactory(localization.service)
    private val handler = RadioListCommandHandler(catalog, viewFactory)

    @Test
    fun `Given the list handler When inspected Then it owns Kord's subcommand name`() {
        assertEquals("list", handler.name)
    }

    @Test
    fun `Given a text only responder When list runs Then fail before reading the catalog`() = runTest {
        val responder: CommandResponder = mockk()

        val error = assertFailsWith<IllegalStateException> { handler.handle(radioContext(), responder) }

        assertTrue(error.message!!.contains("view-capable responder"))
        coVerify(exactly = 0) { catalog.stationPage(any()) }
    }

    @Test
    fun `Given a two page catalog When list runs Then render the first page as three inline columns with both arrows`() =
        runTest {
            coEvery { catalog.stationPage(1) } returns radioPage(
                currentPage = 1,
                maxPage = 2,
                stations = (1..6).map { index -> radioStation("station-$index") },
            )
            val responder = RecordingCommandResponder()

            handler.handle(radioContext(), responder)

            val view = responder.views.single()
            val embed = requireNotNull(view.embed)
            assertEquals(localization.english(RadioMessageKeys.RADIO_LIST_EMBED_TITLE), embed.title)
            assertEquals(localization.english(RadioMessageKeys.RADIO_LIST_EMBED_DESCRIPTION), embed.description)
            assertEquals(3, embed.fields.size)
            assertTrue(embed.fields.all { field -> field.inline })
            assertTrue(embed.fields.all { field -> "\u2800" == field.name })
            assertEquals(
                listOf(
                    "station-1\u2800\u2800\nstation-2",
                    "station-3\u2800\u2800\nstation-4",
                    "station-5\u2800\u2800\nstation-6",
                ),
                embed.fields.map { field -> field.value },
            )
            assertEquals(localization.englishFormat(PlayerMessageKeys.PAGINATED_EMBED_FOOTER, 1, 2), embed.footer)
            val buttons = view.rows.single().buttons
            assertEquals(ComponentId.RADIO_LIST_PREVIOUS, buttons[0].componentId)
            assertEquals(ComponentId.RADIO_LIST_NEXT, buttons[1].componentId)
            assertTrue(buttons[0].disabled, "the first page disables the previous arrow")
            assertFalse(buttons[1].disabled)
            assertEquals(listOf("1", "1"), buttons.map(ButtonView::payload))
            coVerify(exactly = 1) { catalog.stationPage(1) }
        }

    @Test
    fun `Given a single page catalogue When list runs Then render it without pagination`() = runTest {
        coEvery { catalog.stationPage(1) } returns radioPage(
            currentPage = 1,
            maxPage = 1,
            stations = listOf(radioStation("Rock FM")),
        )
        val responder = RecordingCommandResponder()

        handler.handle(radioContext(), responder)

        val view = responder.views.single()
        assertEquals(localization.englishFormat(PlayerMessageKeys.PAGINATED_EMBED_FOOTER, 1, 1), view.embed?.footer)
        assertTrue(view.rows.isEmpty(), "a single page must not paginate")
    }

    @Test
    fun `Given a failed lookup When list runs Then render Kord's empty first page`() = runTest {
        coEvery { catalog.stationPage(1) } returns null
        val responder = RecordingCommandResponder()

        handler.handle(radioContext(), responder)

        val view = responder.views.single()
        val embed = requireNotNull(view.embed)
        assertTrue(embed.fields.isEmpty())
        assertEquals(localization.englishFormat(PlayerMessageKeys.PAGINATED_EMBED_FOOTER, 1, 1), embed.footer)
        assertTrue(view.rows.isEmpty())
        assertEquals(localization.english(RadioMessageKeys.RADIO_LIST_EMBED_TITLE), embed.title)
    }

    @Test
    fun `Given an empty station page When list runs Then render no fields`() = runTest {
        coEvery { catalog.stationPage(1) } returns radioPage(currentPage = 1, maxPage = 1, stations = emptyList())
        val responder = RecordingCommandResponder()

        handler.handle(radioContext(), responder)

        val embed = requireNotNull(responder.views.single().embed)
        assertTrue(embed.fields.isEmpty())
        assertNull(responder.views.single().content)
    }

    @Test
    fun `Given a guild default locale When list runs Then title footer and arrows use it`() = runTest {
        val spanish = LocaleCode("es-ES")
        coEvery { catalog.stationPage(1) } returns radioPage(
            currentPage = 1,
            maxPage = 2,
            stations = listOf(radioStation("Rock FM")),
        )
        val responder = RecordingCommandResponder()

        handler.handle(radioContext(guildLocale = spanish), responder)

        val view = responder.views.single()
        val embed = requireNotNull(view.embed)
        assertEquals(localization.spanish(RadioMessageKeys.RADIO_LIST_EMBED_TITLE), embed.title)
        assertEquals(localization.spanish(RadioMessageKeys.RADIO_LIST_EMBED_DESCRIPTION), embed.description)
        assertEquals(localization.spanishFormat(PlayerMessageKeys.PAGINATED_EMBED_FOOTER, 1, 2), embed.footer)
        assertEquals(
            localization.spanish(PlayerMessageKeys.PAGINATED_EMBED_PREVIOUS_BUTTON_LABEL),
            view.rows.single().buttons[0].label,
        )
    }
}
