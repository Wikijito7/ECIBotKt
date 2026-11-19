package es.wokis.commands.player

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class QueuePaginationTest {

    private companion object {
        const val FIELD_LIMIT = 1024
        const val SEPARATOR = ", "
    }

    @Test
    fun `Given an empty queue When paginated Then there is no page`() {
        assertEquals(emptyList<String>(), queuePages(emptyList()))
    }

    @Test
    fun `Given a single track When paginated Then it forms one page`() {
        assertEquals(listOf("Artist - Song"), queuePages(listOf(testTrack("Artist - Song"))))
    }

    @Test
    fun `Given a page that exactly fills the field When paginated Then it stays on one page`() {
        val first = "a".repeat(FIELD_LIMIT - SEPARATOR.length - 2)
        val second = "bb"

        val pages = queuePages(listOf(testTrack(first), testTrack(second)))

        assertEquals(1, pages.size)
        assertEquals(FIELD_LIMIT, pages.single().length)
        assertEquals(first + SEPARATOR + second, pages.single())
    }

    @Test
    fun `Given one character over the field limit When paginated Then the track moves to the next page`() {
        val first = "a".repeat(FIELD_LIMIT - SEPARATOR.length - 2)
        val second = "bbb"

        val pages = queuePages(listOf(testTrack(first), testTrack(second)))

        assertEquals(listOf(first, second), pages)
    }

    @Test
    fun `Given a long queue When paginated Then every page stays inside the field limit`() {
        val tracks = (1..12).map { index -> testTrack("$index-" + "x".repeat(300)) }

        val pages = queuePages(tracks)

        assertTrue(pages.size > 1, "the fixture must span several pages")
        pages.forEach { page -> assertTrue(page.length <= FIELD_LIMIT, "page too long: ${page.length}") }
        assertEquals(tracks.map { it.displayName }.joinToString(SEPARATOR), pages.joinToString(SEPARATOR))
    }

    @Test
    fun `Given a payload When decoded Then accept only a positive page number`() {
        assertEquals(1, pageFromPayload(null))
        assertEquals(1, pageFromPayload(""))
        assertEquals(1, pageFromPayload("0"))
        assertEquals(1, pageFromPayload("-3"))
        assertEquals(1, pageFromPayload("second"))
        assertEquals(2, pageFromPayload("2"))
        assertEquals(12, pageFromPayload("12"))
    }

    @Test
    fun `Given a page When moving forward or back Then clamp inside the queue bounds`() {
        assertEquals(2, adjacentPage(currentPage = 1, forward = true, pageCount = 3))
        assertEquals(1, adjacentPage(currentPage = 2, forward = false, pageCount = 3))
        assertEquals(1, adjacentPage(currentPage = 1, forward = false, pageCount = 3))
        assertEquals(3, adjacentPage(currentPage = 3, forward = true, pageCount = 3))
        assertEquals(1, adjacentPage(currentPage = 1, forward = true, pageCount = 0))
        assertEquals(1, adjacentPage(currentPage = 1, forward = false, pageCount = 0))
    }
}
