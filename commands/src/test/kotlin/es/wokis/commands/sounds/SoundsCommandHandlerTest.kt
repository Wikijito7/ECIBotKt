package es.wokis.commands.sounds

import es.wokis.commands.neutral.CommandResponder
import es.wokis.commands.player.PlayerLocalizationFixture
import es.wokis.commands.player.RecordingCommandResponder
import es.wokis.commands.player.commandContext
import es.wokis.commands.sound.SoundsCatalogPort
import es.wokis.domain.locale.LocaleCode
import es.wokis.localization.PlayerMessageKeys
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

class SoundsCommandHandlerTest {

    private val soundsCatalog: SoundsCatalogPort = mockk()
    private val localization = PlayerLocalizationFixture()
    private val viewFactory = SoundsMessageViewFactory(localization.service)
    private val handler = SoundsCommandHandler(soundsCatalog, viewFactory)

    @Test
    fun `Given the sounds handler When inspected Then it owns Kord's command name`() {
        assertEquals("sounds", handler.name)
    }

    @Test
    fun `Given a text only responder When sounds runs Then fail before reading the catalog`() = runTest {
        val responder: CommandResponder = mockk()

        val error = assertFailsWith<IllegalStateException> { handler.handle(commandContext(), responder) }

        assertTrue(error.message!!.contains("view-capable responder"))
        coVerify(exactly = 0) { soundsCatalog.soundNames() }
    }

    @Test
    fun `Given a catalog of 51 names When sounds runs Then render one page with three inline columns`() = runTest {
        val names = (1..51).map { index -> "sound-%02d".format(index) }
        givenSounds(names)
        val responder = RecordingCommandResponder()

        handler.handle(commandContext(), responder)

        val embed = requireNotNull(responder.views.single().embed)
        assertEquals(3, embed.fields.size)
        assertTrue(embed.fields.all { field -> field.inline })
        assertTrue(embed.fields.all { field -> "\u2800" == field.name })
        assertEquals(names.take(50).joinToString("\u2800\u2800\n"), embed.fields[0].value)
        assertEquals("sound-51", embed.fields[1].value)
        assertEquals("\u2800", embed.fields[2].value)
        assertEquals(localization.englishFormat(PlayerMessageKeys.PAGINATED_EMBED_FOOTER, 1, 1), embed.footer)
        assertTrue(responder.views.single().rows.isEmpty(), "a single page must not paginate")
    }

    @Test
    fun `Given a catalog spanning two pages When sounds runs Then render the first page with an enabled next button`() =
        runTest {
            givenLongSounds()
            val responder = RecordingCommandResponder()

            handler.handle(commandContext(), responder)

            val view = responder.views.single()
            val embed = requireNotNull(view.embed)
            assertEquals(localization.englishFormat(PlayerMessageKeys.PAGINATED_EMBED_FOOTER, 1, 2), embed.footer)
            assertEquals(longNames().take(3), embed.fields.map { field -> field.value })
            val buttons = view.rows.single().buttons
            assertTrue(buttons[0].disabled)
            assertFalse(buttons[1].disabled)
        }

    @Test
    fun `Given a catalog When sounds runs Then describe it with the total sound count`() = runTest {
        givenSounds((1..51).map { index -> "sound-%02d".format(index) })
        val responder = RecordingCommandResponder()

        handler.handle(commandContext(), responder)

        val embed = requireNotNull(responder.views.single().embed)
        assertEquals(localization.english(PlayerMessageKeys.SOUNDS_EMBED_TITLE), embed.title)
        assertEquals(
            localization.englishFormat(PlayerMessageKeys.SOUNDS_EMBED_DESCRIPTION, 51),
            embed.description,
        )
    }

    @Test
    fun `Given an empty catalog When sounds runs Then render no fields footer or buttons`() = runTest {
        givenSounds(emptyList())
        val responder = RecordingCommandResponder()

        handler.handle(commandContext(), responder)

        val view = responder.views.single()
        val embed = requireNotNull(view.embed)
        assertTrue(embed.fields.isEmpty())
        assertNull(embed.footer)
        assertTrue(view.rows.isEmpty())
        assertEquals(
            localization.englishFormat(PlayerMessageKeys.SOUNDS_EMBED_DESCRIPTION, 0),
            embed.description,
        )
    }

    @Test
    fun `Given a guild default locale When sounds runs Then title description footer and buttons use it`() = runTest {
        val spanish = LocaleCode("es-ES")
        givenLongSounds()
        val responder = RecordingCommandResponder()

        handler.handle(commandContext(guildLocale = spanish), responder)

        val view = responder.views.single()
        val embed = requireNotNull(view.embed)
        assertEquals(localization.spanish(PlayerMessageKeys.SOUNDS_EMBED_TITLE), embed.title)
        assertEquals(
            localization.spanishFormat(PlayerMessageKeys.SOUNDS_EMBED_DESCRIPTION, 4),
            embed.description,
        )
        assertEquals(localization.spanishFormat(PlayerMessageKeys.PAGINATED_EMBED_FOOTER, 1, 2), embed.footer)
        assertEquals(
            localization.spanish(PlayerMessageKeys.PAGINATED_EMBED_PREVIOUS_BUTTON_LABEL),
            view.rows.single().buttons[0].label,
        )
    }

    private fun givenSounds(names: List<String>) {
        coEvery { soundsCatalog.soundNames() } returns names
    }

    private fun givenLongSounds() {
        givenSounds(longNames())
    }

    private fun longNames(): List<String> = listOf("a".repeat(600), "b".repeat(600), "c".repeat(600), "d".repeat(600))
}
