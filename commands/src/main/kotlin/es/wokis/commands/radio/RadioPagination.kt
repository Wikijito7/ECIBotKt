package es.wokis.commands.radio

import es.wokis.message.ComponentId

/** Columns per radio page, matching Kord's `RADIO_LIST_COLUMNS`. */
internal const val RADIO_LIST_COLUMNS = 3

/** Kord truncates station names to this length before rendering them. */
internal const val RADIO_NAME_MAX_LENGTH = 20

/** Country codes per `/radio countrycodes` page; the list is grouped into fields per page. */
internal const val RADIO_COUNTRY_CODES_PER_PAGE = 100

internal const val FIRST_RADIO_PAGE = 1

/** Shared guard message for every radio embed reply. */
internal const val RADIO_VIEW_RESPONDER_REQUIRED =
    "The radio reply is an embed, so the adapter must provide a view-capable responder"

private const val ELLIPSIS = "…"
private const val ESCAPE_PREFIX = "\\"
private const val COLUMN_SEPARATOR = "\u2800\u2800\n"
private const val PAYLOAD_SEPARATOR = "::"
private val LEADING_MARKER = Regex("^[#*-]")

/**
 * Pagination state one rendered radio page carries on its buttons.
 *
 * [query] is the search term for `/radio search` pages and null for `/radio list`, so each page
 * recomputes the same lookup Kord's `RadioSearchNameCommand`/`RadioSearchCountryCodeCommand` use.
 */
data class RadioPageRequest(
    val currentPage: Int,
    val pageCount: Int,
    val previous: ComponentId,
    val next: ComponentId,
    val query: String? = null,
)

/** Kord's display columns: escaped, truncated names packed into at most three columns. */
internal fun radioColumns(stations: List<RadioStation>): List<String> = stations
    .map { station -> displayName(station.name) }
    .chunked((stations.size / RADIO_LIST_COLUMNS).coerceAtLeast(1))
    .map { column -> column.joinToString(COLUMN_SEPARATOR) }

/**
 * Wire payload for a radio page button: `page` for the plain list, `page::query` for a search.
 *
 * The neutral component context only receives the payload, so the page travels with it. Splitting
 * on the `::` separator recovers the page and keeps the query intact, including a query containing
 * the separator itself.
 */
internal fun radioPagePayload(page: Int, query: String?): String =
    if (query.isNullOrEmpty()) page.toString() else "$page$PAYLOAD_SEPARATOR$query"

/** 1-based page encoded in a radio payload; the first page when absent or malformed. */
internal fun radioPageFromPayload(payload: String?): Int =
    payload?.substringBefore(PAYLOAD_SEPARATOR)?.toIntOrNull()?.takeIf { it >= FIRST_RADIO_PAGE }
        ?: FIRST_RADIO_PAGE

/** Search term encoded in a radio payload, or null when the payload carries none. */
internal fun radioQueryFromPayload(payload: String?): String? =
    payload?.substringAfter(PAYLOAD_SEPARATOR, "")?.takeIf(String::isNotEmpty)

/** Country-code pages: Kord's field grouping is applied per page, so each page stays inside limits. */
internal fun radioCountryCodePages(countryCodes: List<String>): List<List<String>> =
    countryCodes.chunked(RADIO_COUNTRY_CODES_PER_PAGE)

/** Page reached by one step in [forward]'s direction, never below the first page. */
internal fun adjacentRadioPage(currentPage: Int, forward: Boolean): Int =
    (currentPage + if (forward) 1 else -1).coerceAtLeast(FIRST_RADIO_PAGE)

private fun displayName(name: String): String =
    (if (LEADING_MARKER.containsMatchIn(name)) ESCAPE_PREFIX else "") + name.takeAtMost(RADIO_NAME_MAX_LENGTH)

/**
 * Kord's `takeAtMost`, shared by the rendered names and the autocomplete choices: values at or
 * above [maxLength] are trimmed and ellipsized.
 */
internal fun String.takeAtMost(maxLength: Int): String =
    if (length < maxLength) this else take(maxLength - 2).trim() + ELLIPSIS
