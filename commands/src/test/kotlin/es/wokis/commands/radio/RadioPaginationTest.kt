package es.wokis.commands.radio

import es.wokis.message.ComponentId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class RadioPaginationTest {

    @Test
    fun `Given an empty station list When rendered into columns Then there is no column`() {
        assertEquals(emptyList<String>(), radioColumns(emptyList()))
    }

    @Test
    fun `Given one station When rendered into columns Then it forms a single column`() {
        assertEquals(listOf("Rock FM"), radioColumns(listOf(radioStation("Rock FM"))))
    }

    @Test
    fun `Given six stations When rendered into columns Then pack them into three two-name columns`() {
        val stations = (1..6).map { index -> radioStation("station-$index") }

        val columns = radioColumns(stations)

        assertEquals(3, columns.size)
        assertEquals(listOf("station-1\u2800\u2800\nstation-2", "station-3\u2800\u2800\nstation-4"), columns.take(2))
        assertEquals("station-5\u2800\u2800\nstation-6", columns[2])
    }

    @Test
    fun `Given a long station name When rendered Then truncate it like Kord's takeAtMost`() {
        val columns = radioColumns(listOf(radioStation("x".repeat(20))))

        assertEquals(listOf("x".repeat(18) + "…"), columns)
        assertEquals(19, columns.single().length)
    }

    @Test
    fun `Given a name shorter than the limit When rendered Then keep it untouched`() {
        val name = "y".repeat(19)

        assertEquals(listOf(name), radioColumns(listOf(radioStation(name))))
    }

    @Test
    fun `Given a name starting with Kord's marker characters When rendered Then escape only the leading marker`() {
        val stations = listOf(
            radioStation("#one"),
            radioStation("*two"),
            radioStation("-three"),
            radioStation("mid-dle"),
        )

        assertEquals(listOf("\\#one", "\\*two", "\\-three", "mid-dle"), radioColumns(stations))
    }

    @Test
    fun `Given a plain list page When encoded Then carry only the page`() {
        assertEquals("2", radioPagePayload(page = 2, query = null))
        assertEquals("2", radioPagePayload(page = 2, query = ""))
    }

    @Test
    fun `Given a search page When encoded Then carry the page and the query`() {
        assertEquals("2::Rock", radioPagePayload(page = 2, query = "Rock"))
        assertEquals("2::Rock::Live", radioPagePayload(page = 2, query = "Rock::Live"))
    }

    @Test
    fun `Given a payload When the page is decoded Then accept only a positive page number`() {
        assertEquals(1, radioPageFromPayload(null))
        assertEquals(1, radioPageFromPayload(""))
        assertEquals(1, radioPageFromPayload("0"))
        assertEquals(1, radioPageFromPayload("-3"))
        assertEquals(1, radioPageFromPayload("second"))
        assertEquals(2, radioPageFromPayload("2"))
        assertEquals(12, radioPageFromPayload("12::Rock"))
    }

    @Test
    fun `Given a payload When the search query is decoded Then keep everything after the separator`() {
        assertNull(radioQueryFromPayload(null))
        assertNull(radioQueryFromPayload(""))
        assertNull(radioQueryFromPayload("2"))
        assertNull(radioQueryFromPayload("2::"))
        assertEquals("Rock", radioQueryFromPayload("2::Rock"))
        assertEquals("Rock::Live", radioQueryFromPayload("2::Rock::Live"))
        assertEquals("Rock FM ", radioQueryFromPayload("1::Rock FM "))
    }

    @Test
    fun `Given a country code list When paged Then chunk it into Kord's hundred per page`() {
        assertEquals(emptyList<List<String>>(), radioCountryCodePages(emptyList()))
        assertEquals(1, radioCountryCodePages((1..100).map { "C$it" }).size)
        assertEquals(listOf(100, 1), radioCountryCodePages((1..101).map { "C$it" }).map { it.size })
    }

    @Test
    fun `Given a page When moving forward or back Then clamp at the first page`() {
        assertEquals(2, adjacentRadioPage(currentPage = 1, forward = true))
        assertEquals(1, adjacentRadioPage(currentPage = 1, forward = false))
        assertEquals(3, adjacentRadioPage(currentPage = 2, forward = true))
        assertEquals(1, adjacentRadioPage(currentPage = 2, forward = false))
    }

    @Test
    fun `Given the search kinds When inspected Then each owns its option and its id pair`() {
        assertEquals("name", RadioSearchKind.NAME.optionName)
        assertEquals(ComponentId.RADIO_SEARCH_NAME_PREVIOUS, RadioSearchKind.NAME.previousButton)
        assertEquals(ComponentId.RADIO_SEARCH_NAME_NEXT, RadioSearchKind.NAME.nextButton)
        assertEquals("countrycode", RadioSearchKind.COUNTRY_CODE.optionName)
        assertEquals(ComponentId.RADIO_SEARCH_COUNTRY_CODE_PREVIOUS, RadioSearchKind.COUNTRY_CODE.previousButton)
        assertEquals(ComponentId.RADIO_SEARCH_COUNTRY_CODE_NEXT, RadioSearchKind.COUNTRY_CODE.nextButton)
    }
}
