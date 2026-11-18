package es.wokis.commands.radio

import es.wokis.commands.neutral.AutocompleteContext
import es.wokis.commands.neutral.AutocompleteResponder
import io.mockk.CapturingSlot
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RadioAutocompleteHandlersTest {

    private val catalog: RadioCatalogPort = mockk()
    private val responder: AutocompleteResponder = mockk()
    private val playHandler = RadioPlayAutocompleteHandler(catalog)
    private val countryCodeHandler = RadioCountryCodeAutocompleteHandler(catalog)

    @Test
    fun `Given the radio autocomplete handlers When inspected Then they own Kord's command and option names`() {
        assertEquals("radio", playHandler.commandName)
        assertEquals("radio", countryCodeHandler.commandName)
        assertEquals("radio", playHandler.optionName)
        assertEquals("countrycode", countryCodeHandler.optionName)
    }

    @Test
    fun `Given an empty play input When autocomplete runs Then reply no choices without reading the catalog`() = runTest {
        val captured = everyRespond()

        playHandler.handle(context("radio", ""), responder)

        assertEquals(emptyList<String>(), captured.captured)
        coVerify(exactly = 0) { catalog.searchStations(any()) }
    }

    @Test
    fun `Given catalog matches When play autocompletes Then forward the matches in catalog order`() = runTest {
        coEvery { catalog.searchStations("rock") } returns listOf(
            radioStation("rock one"),
            radioStation("hard rock"),
        )
        val captured = everyRespond()

        playHandler.handle(context("radio", "rock"), responder)

        assertEquals(listOf("rock one", "hard rock"), captured.captured)
        coVerify(exactly = 1) { catalog.searchStations("rock") }
    }

    @Test
    fun `Given more than twenty five matches When play autocompletes Then reply only the first twenty five`() = runTest {
        coEvery { catalog.searchStations("rock") } returns (1..30).map { index -> radioStation("rock-%02d".format(index)) }
        val captured = everyRespond()

        playHandler.handle(context("radio", "rock"), responder)

        assertEquals(25, captured.captured.size)
        assertEquals("rock-01", captured.captured.first())
        assertEquals("rock-25", captured.captured.last())
    }

    @Test
    fun `Given a station name over Kord's choice limit When play autocompletes Then truncate it`() = runTest {
        val longName = "x".repeat(98) + "yz"
        coEvery { catalog.searchStations("x") } returns listOf(radioStation(longName))
        val captured = everyRespond()

        playHandler.handle(context("radio", "x"), responder)

        assertEquals(99, captured.captured.single().length)
        assertEquals("x".repeat(98) + "…", captured.captured.single())
    }

    @Test
    fun `Given an empty country code input When autocomplete runs Then reply no choices without reading the catalog`() =
        runTest {
            val captured = everyRespond()

            countryCodeHandler.handle(context("countrycode", ""), responder)

            assertEquals(emptyList<String>(), captured.captured)
            coVerify(exactly = 0) { catalog.countryCodes() }
        }

    @Test
    fun `Given typed country code text When autocomplete runs Then match codes containing it case insensitively`() = runTest {
        coEvery { catalog.countryCodes() } returns listOf("ES", "US", "FR", "DE")
        val captured = everyRespond()

        countryCodeHandler.handle(context("countrycode", "s"), responder)

        assertEquals(listOf("ES", "US"), captured.captured)
    }

    @Test
    fun `Given more than twenty five country codes When autocomplete runs Then reply only the first twenty five`() = runTest {
        coEvery { catalog.countryCodes() } returns (1..30).map { index -> "X%02d".format(index) }
        val captured = everyRespond()

        countryCodeHandler.handle(context("countrycode", "x"), responder)

        assertEquals(25, captured.captured.size)
        assertEquals("X01", captured.captured.first())
    }

    @Test
    fun `Given a failed country code lookup When autocomplete runs Then reply no choices`() = runTest {
        coEvery { catalog.countryCodes() } returns null
        val captured = everyRespond()

        countryCodeHandler.handle(context("countrycode", "e"), responder)

        assertEquals(emptyList<String>(), captured.captured)
    }

    private fun everyRespond(): CapturingSlot<List<String>> {
        val captured = slot<List<String>>()
        coEvery { responder.respond(capture(captured)) } just Runs
        return captured
    }

    private fun context(optionName: String, input: String) = AutocompleteContext(
        commandName = RadioCommandDefinitions.RADIO_COMMAND_NAME,
        optionName = optionName,
        guildId = null,
        input = input,
    )
}
