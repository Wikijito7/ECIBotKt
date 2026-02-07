package es.wokis.utils

import java.net.URLEncoder

private const val URL_ENCODED_SPACE = "+"
private const val SPACE_UTF_8 = "%20"
private val EMOJI_COMBINATION_REGEX = Regex("(\\p{IsEmoji_Presentation}|\\p{IsEmoji_Modifier}|\\p{IsEmoji_Modifier_Base}|\\p{IsEmoji_Component}|\\p{IsExtended_Pictographic})+")

fun String.takeIfNotEmpty() = takeIf { it.isNotEmpty() }

fun String.isValidUrl(): Boolean =
    startsWith("http://") || startsWith("https://")

fun String.asEncodedUrl() = URLEncoder.encode(this, Charsets.UTF_8).replace(URL_ENCODED_SPACE, SPACE_UTF_8)

fun String.takeAtMost(maxLength: Int) = if (length < maxLength) this else this.take(maxLength - 2).trim().plus("…")

/**
 * Creates a markdown link with emojis excluded from the link text.
 * Discord doesn't render markdown links correctly when emojis are inside the link brackets,
 * so this function splits the text around emojis to create multiple links.
 *
 * Example: "I love the 🍆 emoji" with url "https://example.com" becomes
 * `[I love the ](https://example.com)🍆[ emoji](https://example.com)`
 */
fun String.toMarkdownLinkEmojiAware(url: String): String {
    val originalText = this
    return buildString {
        var lastEnd = 0
        EMOJI_COMBINATION_REGEX.findAll(originalText).forEach { match ->
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
