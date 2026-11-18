package es.wokis.jda.radio

import es.wokis.commands.radio.RadioPage
import es.wokis.commands.radio.RadioStation
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

class JdaRadioCatalogPortTest {

    private val http: RadioCatalogHttp = mockk()
    private val port = JdaRadioCatalogPort(http)

    @Test
    fun `Given the plain page endpoint When stationPage runs Then read Kord's page payload`() = runTest {
        coEvery { http.get("/radio/page/2") } returns pageJson()

        val page = port.stationPage(2)

        assertEquals(RadioPage(currentPage = 2, maxPage = 5, stations = listOf(rockFm())), page)
    }

    @Test
    fun `Given a name search When searchByName runs Then URL encode the query and read the page`() = runTest {
        coEvery { http.get("/radio/find/name/Rock%20%26%20Roll/page/1") } returns pageJson()

        val page = port.searchByName("Rock & Roll", 1)

        assertEquals(listOf(rockFm()), page?.stations)
        coVerify(exactly = 1) { http.get("/radio/find/name/Rock%20%26%20Roll/page/1") }
    }

    @Test
    fun `Given a country code search When searchByCountryCode runs Then read Kord's country endpoint`() = runTest {
        coEvery { http.get("/radio/find/countrycode/ES/page/3") } returns pageJson()

        val page = port.searchByCountryCode("ES", 3)

        assertEquals(2, page?.currentPage)
        coVerify(exactly = 1) { http.get("/radio/find/countrycode/ES/page/3") }
    }

    @Test
    fun `Given a typed name When findStation runs Then drop the last character like Kord's RadioService`() = runTest {
        coEvery { http.get("/radio/find/name/Roc") } returns stationsJson()

        val station = port.findStation("Rock")

        assertEquals(rockFm(), station)
        coVerify(exactly = 1) { http.get("/radio/find/name/Roc") }
    }

    @Test
    fun `Given a typed name with encodable characters When findStation runs Then encode the shortened name`() = runTest {
        coEvery { http.get("/radio/find/name/Rock%20%26%20Rol") } returns stationsJson()

        port.findStation("Rock & Roll")

        coVerify(exactly = 1) { http.get("/radio/find/name/Rock%20%26%20Rol") }
    }

    @Test
    fun `Given several matches When findStation runs Then keep only the first station`() = runTest {
        coEvery { http.get("/radio/find/name/Roc") } returns twoStationsJson()

        val station = port.findStation("Rock")

        assertEquals("Rock FM", station?.name)
    }

    @Test
    fun `Given an empty match list When findStation runs Then return null`() = runTest {
        coEvery { http.get("/radio/find/name/Roc") } returns "[]"

        assertNull(port.findStation("Rock"))
    }

    @Test
    fun `Given autocomplete text When searchStations runs Then map every match without the drop quirk`() = runTest {
        coEvery { http.get("/radio/find/name/rock") } returns twoStationsJson()

        val stations = port.searchStations("rock")

        assertEquals(listOf("Rock FM", "Rock Two"), stations.map { station -> station.name })
        coVerify(exactly = 1) { http.get("/radio/find/name/rock") }
    }

    @Test
    fun `Given the random endpoint When randomStation runs Then read Kord's station payload`() = runTest {
        coEvery { http.get("/radio/random") } returns ROCK_FM_JSON

        assertEquals(rockFm(), port.randomStation())
    }

    @Test
    fun `Given the country code endpoint When countryCodes runs Then read Kord's code list`() = runTest {
        coEvery { http.get("/radio/countrycodes") } returns """{"countryCode":["ES","US"]}"""

        assertEquals(listOf("ES", "US"), port.countryCodes())
    }

    @Test
    fun `Given a transport failure When any lookup runs Then report the adapter's contract value`() = runTest {
        coEvery { http.get(any()) } throws IllegalStateException("endpoint unavailable")

        assertNull(port.stationPage(1))
        assertNull(port.searchByName("Rock", 1))
        assertNull(port.searchByCountryCode("ES", 1))
        assertNull(port.findStation("Rock"))
        assertNull(port.randomStation())
        assertNull(port.countryCodes())
        assertEquals(emptyList<RadioStation>(), port.searchStations("rock"))
    }

    @Test
    fun `Given a decode failure When a page lookup runs Then return null instead of throwing`() = runTest {
        coEvery { http.get(any()) } returns "not-json"

        assertNull(port.stationPage(1))
        assertEquals(emptyList<RadioStation>(), port.searchStations("rock"))
    }

    @Test
    fun `Given a cancelled lookup When it runs Then propagate the cancellation`() = runTest {
        coEvery { http.get(any()) } throws CancellationException("cancelled")

        assertFailsWith<CancellationException> { port.stationPage(1) }
    }

    @Test
    fun `Given the shared debug flag When the radio endpoint is selected Then dev and production match Kord`() {
        assertEquals("https://ecibot.wokis.es/api", radioEndpoint(debug = false))
        assertEquals("https://pre-ecibot.wokis.es/api", radioEndpoint(debug = true))
    }

    private fun rockFm(): RadioStation = RadioStation(
        name = "Rock FM",
        streamUrl = "https://stream",
        faviconUrl = "https://favicon",
        countryCode = "ES",
    )

    private fun pageJson(): String = """{"currentPage":2,"maxPage":5,"radios":[$ROCK_FM_JSON]}"""

    private fun stationsJson(): String = "[$ROCK_FM_JSON]"

    private fun twoStationsJson(): String = "[" +
        "$ROCK_FM_JSON," +
        """{"name":"Rock Two","url":"https://two","favicon":"https://two.png","countrycode":"US"}""" +
        "]"

    private companion object {
        const val ROCK_FM_JSON =
            """{"name":"Rock FM","url":"https://stream","favicon":"https://favicon","countrycode":"ES"}"""
    }
}
