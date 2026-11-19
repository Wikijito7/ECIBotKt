package es.wokis.commands.radio

/**
 * One station exposed by the radio catalog. Field names avoid gateway/HTTP vocabulary:
 * [streamUrl] is what the player loads and [faviconUrl] is the station image.
 */
data class RadioStation(
    val name: String,
    val streamUrl: String,
    val faviconUrl: String,
    val countryCode: String,
)

/** One catalog page: the 1-based [currentPage], the last [maxPage] and its [stations]. */
data class RadioPage(
    val currentPage: Int,
    val maxPage: Int,
    val stations: List<RadioStation>,
)

/**
 * Gateway-neutral seam over the radio catalog the runtimes resolve stations from.
 *
 * Every lookup reports a transport or decode failure as `null`, never as an exception, so handlers
 * can render Kord's error/empty branches without knowing the source (HTTP today). An empty list is
 * a valid success value only where the underlying lookup can return an empty collection.
 */
interface RadioCatalogPort {

    /** One page of the full station list; the plain `/radio list` source. */
    suspend fun stationPage(page: Int): RadioPage?

    /** One page of stations whose name matches [query]. */
    suspend fun searchByName(query: String, page: Int): RadioPage?

    /** One page of stations in [countryCode]. */
    suspend fun searchByCountryCode(countryCode: String, page: Int): RadioPage?

    /** The station `/radio play` resolves [name] to, or null when nothing matches or the lookup fails. */
    suspend fun findStation(name: String): RadioStation?

    /** Name matches used by the `/radio play` autocomplete; empty when nothing matches or the lookup fails. */
    suspend fun searchStations(query: String): List<RadioStation>

    /** The station `/radio random` plays, or null when the lookup fails or returns nothing. */
    suspend fun randomStation(): RadioStation?

    /** Every known country code; null when the lookup fails and empty when none is known. */
    suspend fun countryCodes(): List<String>?
}
