package es.wokis.jda.radio

import es.wokis.commands.radio.RadioCatalogPort
import es.wokis.commands.radio.RadioPage
import es.wokis.commands.radio.RadioStation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

private const val PRE_ECIBOT_ENDPOINT = "https://pre-ecibot.wokis.es/api"
private const val PRO_ECIBOT_ENDPOINT = "https://ecibot.wokis.es/api"
private const val CONNECT_TIMEOUT_SECONDS = 10L

/** Whole-request budget: a hung response must not pin an IO thread past the connect budget. */
private const val REQUEST_TIMEOUT_SECONDS = CONNECT_TIMEOUT_SECONDS
private const val URL_ENCODED_SPACE = "+"
private const val SPACE_UTF_8 = "%20"

/** The catalog endpoint the Kord `RadioService` uses for the same shared configuration flag. */
internal fun radioEndpoint(debug: Boolean): String = if (debug) PRE_ECIBOT_ENDPOINT else PRO_ECIBOT_ENDPOINT

/**
 * Narrow seam over the catalog HTTP calls, so the port can be tested without a live endpoint.
 * Implementations must throw for a transport failure or a non-success status.
 */
internal fun interface RadioCatalogHttp {

    suspend fun get(path: String): String
}

/**
 * Production [RadioCatalogHttp] over the JDK client — no extra HTTP dependency in this module.
 *
 * Requests are blocking, so they run on [Dispatchers.IO] and carry a request-level timeout for the
 * same 10s budget as the connect timeout; a non-2xx status throws and the port turns it into Kord's
 * null result.
 */
internal class JdkRadioCatalogHttp(
    private val baseUrl: String,
    private val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS))
        .build(),
) : RadioCatalogHttp {

    override suspend fun get(path: String): String {
        val request = HttpRequest.newBuilder(URI.create(baseUrl + path))
            .timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
            .GET()
            .build()
        val response = withContext(Dispatchers.IO) {
            client.send(request, HttpResponse.BodyHandlers.ofString())
        }
        check(response.statusCode() in SUCCESS_RANGE) {
            "The radio catalog answered with status ${response.statusCode()}"
        }
        return response.body()
    }

    private companion object {
        private val SUCCESS_RANGE = 200..299
    }
}

/**
 * JDA-side [RadioCatalogPort]: the same pre/pro ecibot endpoints and payload shapes the Kord
 * `RadioService` reads, decoded into the neutral catalog model.
 *
 * Kord quirk kept for parity: `RadioService.findRadio` drops the last character of the typed name
 * before the lookup (`dropLast(1)`), so search-by-name behaves identically on both runtimes.
 */
internal class JdaRadioCatalogPort(
    private val http: RadioCatalogHttp,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : RadioCatalogPort {

    override suspend fun stationPage(page: Int): RadioPage? = lookup {
        json.decodeFromString<RadioPageJson>(http.get("/radio/page/$page")).toModel()
    }

    override suspend fun searchByName(query: String, page: Int): RadioPage? = lookup {
        json.decodeFromString<RadioPageJson>(
            http.get("/radio/find/name/${query.encoded()}/page/$page"),
        ).toModel()
    }

    override suspend fun searchByCountryCode(countryCode: String, page: Int): RadioPage? = lookup {
        json.decodeFromString<RadioPageJson>(
            http.get("/radio/find/countrycode/${countryCode.encoded()}/page/$page"),
        ).toModel()
    }

    override suspend fun findStation(name: String): RadioStation? = lookup {
        json.decodeFromString<List<RadioStationJson>>(
            http.get("/radio/find/name/${name.dropLast(1).encoded()}"),
        ).firstOrNull()?.toModel()
    }

    override suspend fun searchStations(query: String): List<RadioStation> = lookup {
        json.decodeFromString<List<RadioStationJson>>(
            http.get("/radio/find/name/${query.encoded()}"),
        ).map { station -> station.toModel() }
    } ?: emptyList()

    override suspend fun randomStation(): RadioStation? = lookup {
        json.decodeFromString<RadioStationJson>(http.get("/radio/random")).toModel()
    }

    override suspend fun countryCodes(): List<String>? = lookup {
        json.decodeFromString<CountryCodesJson>(http.get("/radio/countrycodes")).countryCodes
    }

    private suspend fun <T> lookup(block: suspend () -> T): T? = try {
        block()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Exception) {
        LOG.debug("The radio catalog lookup failed ({})", error.javaClass.simpleName)
        null
    }

    private companion object {
        private val LOG = LoggerFactory.getLogger(JdaRadioCatalogPort::class.java)
    }
}

/** Kord's `asEncodedUrl`: URL-encoded with spaces as `%20`. */
private fun String.encoded(): String =
    URLEncoder.encode(this, Charsets.UTF_8).replace(URL_ENCODED_SPACE, SPACE_UTF_8)

@Serializable
private data class RadioStationJson(
    @SerialName("name") val name: String,
    @SerialName("url") val streamUrl: String,
    @SerialName("favicon") val faviconUrl: String,
    @SerialName("countrycode") val countryCode: String,
) {
    fun toModel(): RadioStation = RadioStation(
        name = name,
        streamUrl = streamUrl,
        faviconUrl = faviconUrl,
        countryCode = countryCode,
    )
}

@Serializable
private data class RadioPageJson(
    @SerialName("currentPage") val currentPage: Int,
    @SerialName("maxPage") val maxPage: Int,
    @SerialName("radios") val stations: List<RadioStationJson>,
) {
    fun toModel(): RadioPage = RadioPage(
        currentPage = currentPage,
        maxPage = maxPage,
        stations = stations.map { station -> station.toModel() },
    )
}

@Serializable
private data class CountryCodesJson(
    @SerialName("countryCode") val countryCodes: List<String>,
)
