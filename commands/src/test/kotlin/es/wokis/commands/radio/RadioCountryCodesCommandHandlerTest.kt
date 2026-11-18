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

class RadioCountryCodesCommandHandlerTest {

    private val catalog: RadioCatalogPort = mockk()
    private val localization = RadioLocalizationFixture()
    private val viewFactory = RadioMessageViewFactory(localization.service)
    private val handler = RadioCountryCodesCommandHandler(catalog, viewFactory, localization.service)

    @Test
    fun `Given the country codes handler When inspected Then it owns Kord's subcommand name`() {
        assertEquals("countrycodes", handler.name)
    }

    @Test
    fun `Given a failed lookup When countrycodes runs Then reply Kord's error text`() = runTest {
        coEvery { catalog.countryCodes() } returns null
        val responder = RecordingCommandResponder()

        handler.handle(radioContext(), responder)

        assertEquals(
            listOf(localization.english(RadioMessageKeys.RADIO_COUNTRYCODES_ERROR)),
            responder.responses.map { response -> response.content },
        )
        assertTrue(responder.views.isEmpty())
    }

    @Test
    fun `Given an empty lookup When countrycodes runs Then reply Kord's empty text`() = runTest {
        coEvery { catalog.countryCodes() } returns emptyList()
        val responder = RecordingCommandResponder()

        handler.handle(radioContext(), responder)

        assertEquals(
            listOf(localization.english(RadioMessageKeys.RADIO_COUNTRYCODES_EMPTY)),
            responder.responses.map { response -> response.content },
        )
        assertTrue(responder.views.isEmpty())
    }

    @Test
    fun `Given a text only responder When countrycodes runs Then fail before rendering the embed`() = runTest {
        coEvery { catalog.countryCodes() } returns listOf("ES")
        val responder: CommandResponder = mockk()

        val error = assertFailsWith<IllegalStateException> { handler.handle(radioContext(), responder) }

        assertTrue(error.message!!.contains("view-capable responder"))
        coVerify(exactly = 1) { catalog.countryCodes() }
    }

    @Test
    fun `Given a single page of codes When countrycodes runs Then render Kord's flag field without pagination`() = runTest {
        coEvery { catalog.countryCodes() } returns listOf("ES", "US")
        val responder = RecordingCommandResponder()

        handler.handle(radioContext(), responder)

        val view = responder.views.single()
        val embed = requireNotNull(view.embed)
        assertEquals(localization.english(RadioMessageKeys.RADIO_COUNTRYCODES_EMBED_TITLE), embed.title)
        assertEquals(1, embed.fields.size)
        assertEquals("\u2800", embed.fields.single().name)
        assertFalse(embed.fields.single().inline)
        assertEquals("🇪🇸 ES\u2800🇺🇸 US", embed.fields.single().value)
        assertNull(embed.footer, "a single page declares no footer")
        assertTrue(view.rows.isEmpty(), "a single page emits no arrows")
    }

    @Test
    fun `Given the unknown code When countrycodes runs Then render Kord's question mark flag`() = runTest {
        coEvery { catalog.countryCodes() } returns listOf("UNK")
        val responder = RecordingCommandResponder()

        handler.handle(radioContext(), responder)

        assertEquals("❓ UNK", requireNotNull(responder.views.single().embed).fields.single().value)
    }

    @Test
    fun `Given eight codes When countrycodes runs Then group them eight per line`() = runTest {
        val codes = listOf("AA", "BB", "CC", "DD", "EE", "FF", "GG", "HH", "II")
        coEvery { catalog.countryCodes() } returns codes
        val responder = RecordingCommandResponder()

        handler.handle(radioContext(), responder)

        val lines = requireNotNull(responder.views.single().embed).fields.single().value.split("\n")
        assertEquals(2, lines.size)
        assertEquals(8, lines[0].split("\u2800").size)
        assertEquals("🇮🇮 II", lines[1])
    }

    @Test
    fun `Given exactly one hundred codes When countrycodes runs Then keep Kord's single page output`() = runTest {
        coEvery { catalog.countryCodes() } returns (1..100).map { index -> "C$index" }
        val responder = RecordingCommandResponder()

        handler.handle(radioContext(), responder)

        val view = responder.views.single()
        assertNull(view.embed?.footer)
        assertTrue(view.rows.isEmpty())
    }

    @Test
    fun `Given more than one hundred codes When countrycodes runs Then emit both arrows around the first page`() = runTest {
        coEvery { catalog.countryCodes() } returns (1..101).map { index -> "C$index" }
        val responder = RecordingCommandResponder()

        handler.handle(radioContext(), responder)

        val view = responder.views.single()
        assertEquals(localization.englishFormat(PlayerMessageKeys.PAGINATED_EMBED_FOOTER, 1, 2), view.embed?.footer)
        val buttons = view.rows.single().buttons
        assertEquals(
            listOf(ComponentId.RADIO_COUNTRYCODES_PREVIOUS, ComponentId.RADIO_COUNTRYCODES_NEXT),
            buttons.map(ButtonView::componentId),
        )
        assertTrue(buttons[0].disabled)
        assertFalse(buttons[1].disabled)
        assertEquals(listOf("1", "1"), buttons.map(ButtonView::payload))
    }

    @Test
    fun `Given a guild default locale When countrycodes runs Then render that locale title`() = runTest {
        val spanish = LocaleCode("es-ES")
        coEvery { catalog.countryCodes() } returns listOf("ES")
        val responder = RecordingCommandResponder()

        handler.handle(radioContext(guildLocale = spanish), responder)

        assertEquals(
            localization.spanish(RadioMessageKeys.RADIO_COUNTRYCODES_EMBED_TITLE),
            requireNotNull(responder.views.single().embed).title,
        )
    }
}
