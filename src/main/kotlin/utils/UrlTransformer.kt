package es.wokis.utils

private const val MONOCHROME_KEYWORD = "monochrome"
private const val TIDAL_DOMAIN = "tidal.com"

object UrlTransformer {

    fun transformMonochromeToTidal(url: String): String {
        val monochromeIndex = url.indexOf(MONOCHROME_KEYWORD, ignoreCase = true)
        if (monochromeIndex == -1) {
            return url
        }
        
        // Find the start of the domain (after ://)
        val protocolEndIndex = url.indexOf("://")
        val domainStartIndex = if (protocolEndIndex != -1) {
            protocolEndIndex + 3
        } else {
            0
        }
        
        // Find the end of the monochrome domain (first / after monochrome)
        val domainEndIndex = url.indexOf("/", startIndex = monochromeIndex)
        val path = if (domainEndIndex != -1) {
            url.substring(domainEndIndex)
        } else {
            ""
        }
        
        // Construct new URL: protocol + tidal.com + path
        val protocol = if (protocolEndIndex != -1) {
            url.substring(0, protocolEndIndex + 3)
        } else {
            "https://"
        }
        
        return "$protocol$TIDAL_DOMAIN$path"
    }
}

fun String.transformUrl(): String {
    return UrlTransformer.transformMonochromeToTidal(this)
}
