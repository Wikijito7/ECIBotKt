package es.wokis.commands.sounds

import es.wokis.commands.player.PlayerLocalizationFixture
import es.wokis.localization.PlayerMessageKeys
import es.wokis.message.ButtonStyle
import es.wokis.message.ComponentId
import es.wokis.player.message.PLAYER_BRAND_COLOR
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SoundsMessageViewFactoryTest {

    private val localization = PlayerLocalizationFixture()
    private val factory = SoundsMessageViewFactory(localization.service)

    @Test
    fun `Given 51 names When rendered Then pack fifty per column and keep three inline braille fields`() = runTest {
        val names = shortNames(51)

        val embed = requireNotNull(factory.createView(names, page = 1).embed)

        assertEquals(3, embed.fields.size)
        assertTrue(embed.fields.all { field -> field.inline })
        assertTrue(embed.fields.all { field -> SOUND_COLUMN_NAME == field.name })
        assertEquals(separated(names.take(50)), embed.fields[0].value)
        assertEquals("sound-51", embed.fields[1].value)
        assertEquals(SOUND_COLUMN_NAME, embed.fields[2].value, "a missing column keeps the braille placeholder")
    }

    @Test
    fun `Given unsorted names When rendered Then sort them inside the first column`() = runTest {
        val embed = requireNotNull(factory.createView(listOf("zeta", "alpha", "Beta"), page = 1).embed)

        assertEquals("Beta\u2800\u2800\nalpha\u2800\u2800\nzeta", embed.fields[0].value)
        assertEquals(SOUND_COLUMN_NAME, embed.fields[1].value)
        assertEquals(SOUND_COLUMN_NAME, embed.fields[2].value)
    }

    @Test
    fun `Given names longer than the field limit When rendered Then start a new column for each name`() = runTest {
        val names = longNames()

        val firstPage = requireNotNull(factory.createView(names, page = 1).embed)

        assertEquals(names.take(3), firstPage.fields.map { field -> field.value })
        assertTrue(firstPage.fields.all { field -> field.value.length <= 1024 })
    }

    @Test
    fun `Given four columns When rendered Then paginate three columns per page`() = runTest {
        val view = factory.createView(longNames(), page = 1)

        val embed = requireNotNull(view.embed)
        assertEquals(localization.englishFormat(PlayerMessageKeys.PAGINATED_EMBED_FOOTER, 1, 2), embed.footer)
        assertEquals(1, view.rows.size)
    }

    @Test
    fun `Given the second page When rendered Then only the remaining column keeps its names`() = runTest {
        val embed = requireNotNull(factory.createView(longNames(), page = 2).embed)

        assertEquals("d".repeat(600), embed.fields[0].value)
        assertEquals(SOUND_COLUMN_NAME, embed.fields[1].value)
        assertEquals(SOUND_COLUMN_NAME, embed.fields[2].value)
        assertEquals(localization.englishFormat(PlayerMessageKeys.PAGINATED_EMBED_FOOTER, 2, 2), embed.footer)
    }

    @Test
    fun `Given a catalog When rendered Then describe it with the total count title and brand color`() = runTest {
        val embed = requireNotNull(factory.createView(shortNames(51), page = 1).embed)

        assertEquals(localization.english(PlayerMessageKeys.SOUNDS_EMBED_TITLE), embed.title)
        assertEquals(
            localization.englishFormat(PlayerMessageKeys.SOUNDS_EMBED_DESCRIPTION, 51),
            embed.description,
        )
        assertEquals(PLAYER_BRAND_COLOR, embed.color)
    }

    @Test
    fun `Given a single page When rendered Then omit the buttons`() = runTest {
        val view = factory.createView(shortNames(51), page = 1)

        assertTrue(view.rows.isEmpty())
        assertEquals(localization.englishFormat(PlayerMessageKeys.PAGINATED_EMBED_FOOTER, 1, 1), view.embed?.footer)
    }

    @Test
    fun `Given the first page When rendered Then disable previous and carry the page in both payloads`() = runTest {
        val buttons = factory.createView(longNames(), page = 1).rows.single().buttons

        assertEquals(ComponentId.SOUNDS_PREVIOUS, buttons[0].componentId)
        assertEquals("⬅", buttons[0].emoji)
        assertEquals(ButtonStyle.SECONDARY, buttons[0].style)
        assertTrue(buttons[0].disabled)
        assertEquals("1", buttons[0].payload)
        assertEquals(ComponentId.SOUNDS_NEXT, buttons[1].componentId)
        assertEquals("➡", buttons[1].emoji)
        assertFalse(buttons[1].disabled)
        assertEquals("1", buttons[1].payload)
    }

    @Test
    fun `Given the last page When rendered Then disable next and carry that page`() = runTest {
        val buttons = factory.createView(longNames(), page = 2).rows.single().buttons

        assertFalse(buttons[0].disabled)
        assertTrue(buttons[1].disabled)
        assertEquals("2", buttons[1].payload)
    }

    @Test
    fun `Given a page outside the bounds When rendered Then clamp to the nearest page`() = runTest {
        assertEquals(
            localization.englishFormat(PlayerMessageKeys.PAGINATED_EMBED_FOOTER, 1, 2),
            factory.createView(longNames(), page = 0).embed?.footer,
        )
        assertEquals(
            localization.englishFormat(PlayerMessageKeys.PAGINATED_EMBED_FOOTER, 2, 2),
            factory.createView(longNames(), page = 99).embed?.footer,
        )
    }

    @Test
    fun `Given an empty catalog When rendered Then omit the fields footer and buttons`() = runTest {
        val view = factory.createView(emptyList(), page = 1)

        val embed = requireNotNull(view.embed)
        assertTrue(embed.fields.isEmpty())
        assertNull(embed.footer)
        assertTrue(view.rows.isEmpty())
        assertEquals(
            localization.englishFormat(PlayerMessageKeys.SOUNDS_EMBED_DESCRIPTION, 0),
            embed.description,
        )
    }

    private fun shortNames(count: Int): List<String> = (1..count).map { index -> "sound-%02d".format(index) }

    private fun longNames(): List<String> = listOf("a", "b", "c", "d").map { letter -> letter.repeat(600) }

    private fun separated(names: List<String>): String = names.joinToString("\u2800\u2800\n")
}
