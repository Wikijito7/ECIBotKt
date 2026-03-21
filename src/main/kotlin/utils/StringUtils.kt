package es.wokis.utils

import java.net.URLEncoder

private const val URL_ENCODED_SPACE = "+"
private const val SPACE_UTF_8 = "%20"
private const val EMOJI_REGEX_PATTERN = "(\\p{IsEmoji_Presentation}|\\p{IsEmoji_Modifier}|" +
    "\\p{IsEmoji_Modifier_Base}|\\p{IsEmoji_Component}|\\p{IsExtended_Pictographic})+"
private const val URL_PATTERN_REGEX_PATTERN = "https?://[^\\s]+"
private val PROBLEMATIC_MARKDOWN_PATTERNS_REGEX = Regex("$EMOJI_REGEX_PATTERN|$URL_PATTERN_REGEX_PATTERN")

fun String.takeIfNotEmpty() = takeIf { it.isNotEmpty() }

fun String.isValidUrl(): Boolean =
    startsWith("http://") || startsWith("https://")

fun String.asEncodedUrl() = URLEncoder.encode(this, Charsets.UTF_8).replace(URL_ENCODED_SPACE, SPACE_UTF_8)

fun String.takeAtMost(maxLength: Int) = if (length < maxLength) this else this.take(maxLength - 2).trim().plus("…")

/**
 * Creates a markdown link with special characters (emojis, URLs) excluded from the link text.
 * Discord doesn't render markdown links correctly when emojis or http/https URLs are inside the link brackets,
 * so this function splits the text around these elements to create multiple links.
 *
 * Example: "I love the 🍆 emoji" with url "https://example.com" becomes
 * `[I love the ](https://example.com)🍆[ emoji](https://example.com)`
 */
fun String.toSanitizedMarkdownLink(url: String): String {
    val originalText = this
    return buildString {
        var lastEnd = 0
        PROBLEMATIC_MARKDOWN_PATTERNS_REGEX.findAll(originalText).forEach { match ->
            val textBefore = originalText.substring(lastEnd, match.range.first)
            if (textBefore.isNotEmpty()) {
                append("[$textBefore]($url)")
            }
            append(match.value)
            lastEnd = match.range.last + 1
        }

        val remainingText = originalText.substring(lastEnd)
        if (remainingText.isNotEmpty()) {
            append("[$remainingText]($url)")
        }
    }
}
