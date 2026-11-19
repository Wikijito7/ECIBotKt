package es.wokis.commands.player

import es.wokis.domain.locale.LocaleCode
import es.wokis.domain.model.GuildId
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

class QueueMessageViewFactoryTest {

    private val localization = PlayerLocalizationFixture()
    private val factory = QueueMessageViewFactory(localization.service)

    @Test
    fun `Given a first page When rendered Then the embed description footer and single inline field match Kord`() = runTest {
        val queue = queueSnapshot(
            guildName = "Jungle",
            tracks = listOf(testTrack("a".repeat(600)), testTrack("b".repeat(600))),
        )

        val view = factory.createView(queue, page = 1)

        val embed = requireNotNull(view.embed)
        assertEquals(localization.english(PlayerMessageKeys.QUEUE_EMBED_TITLE), embed.title)
        assertEquals(
            localization.englishFormat(PlayerMessageKeys.QUEUE_EMBED_DESCRIPTION, 2, "Jungle"),
            embed.description,
        )
        assertEquals(PLAYER_BRAND_COLOR, embed.color)
        val field = embed.fields.single()
        assertEquals("\u2800", field.name, "the invisible braille name keeps the column aligned")
        assertTrue(field.inline)
        assertEquals("a".repeat(600), field.value)
        assertEquals(localization.englishFormat(PlayerMessageKeys.PAGINATED_EMBED_FOOTER, 1, 2), embed.footer)
    }

    @Test
    fun `Given a paginated queue When rendered Then only the current page occupies the field`() = runTest {
        val queue = queueSnapshot(tracks = listOf(testTrack("a".repeat(600)), testTrack("b".repeat(600))))

        val second = requireNotNull(factory.createView(queue, page = 2).embed)

        assertEquals("b".repeat(600), second.fields.single().value)
        assertEquals(localization.englishFormat(PlayerMessageKeys.PAGINATED_EMBED_FOOTER, 2, 2), second.footer)
    }

    @Test
    fun `Given the first page When rendered Then the previous button is disabled and next is enabled`() = runTest {
        val queue = queueSnapshot(tracks = listOf(testTrack("a".repeat(600)), testTrack("b".repeat(600))))

        val buttons = factory.createView(queue, page = 1).rows.single().buttons

        assertEquals(ComponentId.QUEUE_PREVIOUS, buttons[0].componentId)
        assertTrue(buttons[0].disabled)
        assertEquals("⬅", buttons[0].emoji)
        assertEquals(ButtonStyle.SECONDARY, buttons[0].style)
        assertEquals("1", buttons[0].payload)
        assertEquals(ComponentId.QUEUE_NEXT, buttons[1].componentId)
        assertFalse(buttons[1].disabled)
        assertEquals("➡", buttons[1].emoji)
        assertEquals("1", buttons[1].payload)
    }

    @Test
    fun `Given the last page When rendered Then the next button is disabled`() = runTest {
        val queue = queueSnapshot(tracks = listOf(testTrack("a".repeat(600)), testTrack("b".repeat(600))))

        val buttons = factory.createView(queue, page = 2).rows.single().buttons

        assertFalse(buttons[0].disabled)
        assertTrue(buttons[1].disabled)
        assertEquals("2", buttons[1].payload)
    }

    @Test
    fun `Given the shipped pagination keys When rendered Then labels come from the catalog`() = runTest {
        val queue = queueSnapshot(tracks = listOf(testTrack("a".repeat(600)), testTrack("b".repeat(600))))

        val buttons = factory.createView(queue, page = 1).rows.single().buttons

        assertEquals(localization.english(PlayerMessageKeys.PAGINATED_EMBED_PREVIOUS_BUTTON_LABEL), buttons[0].label)
        assertEquals(localization.english(PlayerMessageKeys.PAGINATED_EMBED_NEXT_BUTTON_LABEL), buttons[1].label)
    }

    @Test
    fun `Given a single page queue When rendered Then no button row is emitted`() = runTest {
        val view = factory.createView(queueSnapshot(tracks = listOf(testTrack("only"))), page = 1)

        assertTrue(view.rows.isEmpty())
        assertEquals(localization.englishFormat(PlayerMessageKeys.PAGINATED_EMBED_FOOTER, 1, 1), view.embed?.footer)
    }

    @Test
    fun `Given an empty queue When rendered Then there is no field footer or buttons`() = runTest {
        val view = factory.createView(queueSnapshot(), page = 1)

        val embed = requireNotNull(view.embed)
        assertTrue(embed.fields.isEmpty())
        assertNull(embed.footer)
        assertTrue(view.rows.isEmpty())
        assertEquals(
            localization.englishFormat(PlayerMessageKeys.QUEUE_EMBED_DESCRIPTION, 0, "Guild"),
            embed.description,
        )
    }

    @Test
    fun `Given an out of range page When rendered Then clamp to the closest real page`() = runTest {
        val queue = queueSnapshot(tracks = listOf(testTrack("a".repeat(600)), testTrack("b".repeat(600))))

        val above = requireNotNull(factory.createView(queue, page = 99).embed)
        val below = requireNotNull(factory.createView(queue, page = 0).embed)

        assertEquals(localization.englishFormat(PlayerMessageKeys.PAGINATED_EMBED_FOOTER, 2, 2), above.footer)
        assertEquals(localization.englishFormat(PlayerMessageKeys.PAGINATED_EMBED_FOOTER, 1, 2), below.footer)
        assertEquals("a".repeat(600), below.fields.single().value)
    }

    @Test
    fun `Given a guild locale When rendered Then resolve every label through it`() = runTest {
        val queue = queueSnapshot(tracks = listOf(testTrack("a".repeat(600)), testTrack("b".repeat(600))))
        val spanish = LocaleCode("es-ES")

        val view = factory.createView(queue, page = 1, guildId = GuildId(7uL), guildDefaultLocale = spanish)

        val embed = requireNotNull(view.embed)
        assertEquals(localization.spanish(PlayerMessageKeys.QUEUE_EMBED_TITLE), embed.title)
        assertEquals(
            localization.spanish(PlayerMessageKeys.PAGINATED_EMBED_NEXT_BUTTON_LABEL),
            view.rows.single().buttons[1].label,
        )
    }
}
