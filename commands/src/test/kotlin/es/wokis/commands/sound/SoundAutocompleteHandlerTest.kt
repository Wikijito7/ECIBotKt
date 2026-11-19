package es.wokis.commands.sound

import es.wokis.commands.neutral.AutocompleteContext
import es.wokis.commands.neutral.AutocompleteResponder
import es.wokis.commands.player.PlayerCommandDefinitions
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

class SoundAutocompleteHandlerTest {

    private val soundsCatalog: SoundsCatalogPort = mockk()
    private val responder: AutocompleteResponder = mockk()
    private val handler = SoundAutocompleteHandler(soundsCatalog)

    @Test
    fun `Given the sound handler When inspected Then it owns Kord's command and option`() {
        assertEquals(PlayerCommandDefinitions.SOUND_COMMAND_NAME, handler.commandName)
        assertEquals(PlayerCommandDefinitions.SOUND_OPTION_NAME, handler.optionName)
        assertEquals("sound", handler.commandName)
        assertEquals("name", handler.optionName)
    }

    @Test
    fun `Given an empty input When autocomplete runs Then reply no choices without reading the catalog`() = runTest {
        val captured = everyRespond()

        handler.handle(context(""), responder)

        assertEquals(emptyList<String>(), captured.captured)
        coVerify(exactly = 0) { soundsCatalog.soundNames() }
    }

    @Test
    fun `Given a whitespace input with no matching names When autocomplete runs Then reply no choices`() = runTest {
        // No fixture name contains a space, so the emptiness comes from the match: the handler gates on isEmpty(), not isBlank().
        givenSounds("rain", "rainbow")
        val captured = everyRespond()

        handler.handle(context(" "), responder)

        assertEquals(emptyList<String>(), captured.captured)
    }

    @Test
    fun `Given a whitespace input and a name containing a space When autocomplete runs Then suggest that name`() = runTest {
        givenSounds("rain", "heavy rain")
        val captured = everyRespond()

        handler.handle(context(" "), responder)

        assertEquals(listOf("heavy rain"), captured.captured)
    }

    @Test
    fun `Given a query without matches When autocomplete runs Then reply no choices`() = runTest {
        givenSounds("rain", "thunder")
        val captured = everyRespond()

        handler.handle(context("snow"), responder)

        assertEquals(emptyList<String>(), captured.captured)
    }

    @Test
    fun `Given prefix and substring matches When autocomplete runs Then reply the prefix group first`() = runTest {
        givenSounds("nope-rain", "rain", "rainbow", "terrain")
        val captured = everyRespond()

        handler.handle(context("rain"), responder)

        assertEquals(listOf("rain", "rainbow", "nope-rain", "terrain"), captured.captured)
    }

    @Test
    fun `Given mixed case names When autocomplete runs Then match case-insensitively and order by lower case`() = runTest {
        givenSounds("gamma-Rain", "BETA-rain", "alpha-rain")
        val captured = everyRespond()

        handler.handle(context("RAIN"), responder)

        assertEquals(listOf("alpha-rain", "BETA-rain", "gamma-Rain"), captured.captured)
    }

    @Test
    fun `Given a substring match longer than 100 characters When autocomplete runs Then truncate it`() = runTest {
        val longName = "x".repeat(60) + "-rain" + "y".repeat(50)
        givenSounds(longName)
        val captured = everyRespond()

        handler.handle(context("rain"), responder)

        assertEquals(listOf(longName.take(100)), captured.captured)
        assertEquals(100, captured.captured.single().length)
    }

    @Test
    fun `Given more than 25 matches When autocomplete runs Then reply only the first 25`() = runTest {
        givenSounds(*(1..30).map { index -> "rain-%02d".format(index) }.toTypedArray())
        val captured = everyRespond()

        handler.handle(context("rain"), responder)

        assertEquals(25, captured.captured.size)
        assertEquals("rain-01", captured.captured.first())
        assertEquals("rain-25", captured.captured.last())
    }

    @Test
    fun `Given a matching query When autocomplete runs Then answer exactly once`() = runTest {
        givenSounds("rain")
        everyRespond()

        handler.handle(context("rain"), responder)

        coVerify(exactly = 1) { responder.respond(any()) }
    }

    private fun givenSounds(vararg names: String) {
        coEvery { soundsCatalog.soundNames() } returns names.toList()
    }

    private fun everyRespond(): CapturingSlot<List<String>> {
        val captured = slot<List<String>>()
        coEvery { responder.respond(capture(captured)) } just Runs
        return captured
    }

    private fun context(input: String) = AutocompleteContext(
        commandName = PlayerCommandDefinitions.SOUND_COMMAND_NAME,
        optionName = PlayerCommandDefinitions.SOUND_OPTION_NAME,
        guildId = null,
        input = input,
    )
}
