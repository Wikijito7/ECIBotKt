package es.wokis.utils

private const val MONOCHROME_KEYWORD = "monochrome"
private const val TIDAL_DOMAIN = "tidal.com"
private const val PROTOCOL_SEPARATOR = "://"
private const val DEFAULT_PROTOCOL = "https://"
private const val PATH_SEPARATOR = "/"
private const val NOT_FOUND_INDEX = -1

object UrlTransformer {

    fun transformMonochromeToTidal(url: String): String {
        val monochromeIndex = url.indexOf(MONOCHROME_KEYWORD, ignoreCase = true)
        if (monochromeIndex == NOT_FOUND_INDEX) {
            return url
        }

        // Find the start of the domain (after ://)
        val protocolEndIndex = url.indexOf(PROTOCOL_SEPARATOR)
        val protocol = if (protocolEndIndex != NOT_FOUND_INDEX) {
            url.take(protocolEndIndex + PROTOCOL_SEPARATOR.length)
        } else {
            DEFAULT_PROTOCOL
        }

        // Find where the monochrome domain ends (first / after monochrome keyword)
        val domainEndIndex = url.indexOf(PATH_SEPARATOR, startIndex = monochromeIndex)
        val path = if (domainEndIndex != NOT_FOUND_INDEX) {
            url.substring(domainEndIndex)
        } else {
            ""
        }

        return "$protocol$TIDAL_DOMAIN$path"
    }
}

fun String.transformUrl(): String = UrlTransformer.transformMonochromeToTidal(this)
