package es.wokis.utils

import java.net.URLEncoder

private const val URL_ENCODED_SPACE = "+"
private const val SPACE_UTF_8 = "%20"

fun String.takeIfNotEmpty() = takeIf { it.isNotEmpty() }

fun String.isValidUrl(): Boolean =
    startsWith("http://") || startsWith("https://")

fun String.asEncodedUrl() = URLEncoder.encode(this, Charsets.UTF_8).replace(URL_ENCODED_SPACE, SPACE_UTF_8)

fun String.takeAtMost(maxLength: Int) = if (length < maxLength) this else this.take(maxLength - 2).trim().plus("…")
