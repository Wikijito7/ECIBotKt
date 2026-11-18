package es.wokis.jda.player

import java.net.URLEncoder

private const val KOKORO_TTS_URI = "kokoro://?text="
private const val URL_ENCODED_SPACE = "+"
private const val SPACE_UTF_8 = "%20"

/** Mirrors the root TTS service URI: Kokoro with the message URL-encoded and spaces as `%20`. */
internal fun kokoroTtsUri(text: String): String =
    KOKORO_TTS_URI + URLEncoder.encode(text, Charsets.UTF_8).replace(URL_ENCODED_SPACE, SPACE_UTF_8)
