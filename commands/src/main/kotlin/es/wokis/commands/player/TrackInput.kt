package es.wokis.commands.player

private const val HTTP_PROTOCOL = "http://"
private const val HTTPS_PROTOCOL = "https://"
private const val MONOCHROME_KEYWORD = "monochrome"
private const val TIDAL_DOMAIN = "tidal.com"
private const val PROTOCOL_SEPARATOR = "://"
private const val DEFAULT_PROTOCOL = "https://"
private const val PATH_SEPARATOR = "/"
private const val NOT_FOUND_INDEX = -1

/** Kord parity: only `http://` and `https://` inputs are treated as links instead of search terms. */
internal fun String.isTrackUrl(): Boolean = startsWith(HTTP_PROTOCOL) || startsWith(HTTPS_PROTOCOL)

/**
 * Kord parity: a monochrome link is rewritten to the equivalent Tidal link before loading.
 *
 * This mirrors the legacy Kord `UrlTransformer`; the root Kord source stays untouched until Phase 6.
 */
internal fun String.transformMonochromeToTidal(): String {
    val monochromeIndex = indexOf(MONOCHROME_KEYWORD, ignoreCase = true)
    if (monochromeIndex == NOT_FOUND_INDEX) return this

    val protocolEndIndex = indexOf(PROTOCOL_SEPARATOR)
    val protocol = if (protocolEndIndex == NOT_FOUND_INDEX) {
        DEFAULT_PROTOCOL
    } else {
        take(protocolEndIndex + PROTOCOL_SEPARATOR.length)
    }
    val domainEndIndex = indexOf(PATH_SEPARATOR, startIndex = monochromeIndex)
    val path = if (domainEndIndex == NOT_FOUND_INDEX) "" else substring(domainEndIndex)
    return "$protocol$TIDAL_DOMAIN$path"
}
