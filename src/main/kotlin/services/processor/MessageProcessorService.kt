package es.wokis.services.processor

import dev.kord.common.Locale
import dev.kord.common.entity.Snowflake
import dev.kord.core.entity.Message
import dev.kord.core.entity.ReactionEmoji
import es.wokis.dispatchers.AppDispatchers
import es.wokis.localization.LocalizationKeys
import es.wokis.services.localization.LocalizationService
import es.wokis.utils.asRegex
import es.wokis.utils.createCoroutineScope
import es.wokis.utils.getGuildLocale
import kotlinx.coroutines.launch
import java.util.regex.Pattern

private const val FIXED_UP_TWITTER_URL = "https://fixupx.com/status/"
private const val FIXED_UP_INSTAGRAM_URL = "https://ddinstagram.com"
private const val FIXED_UP_REDDIT_URL = "https://rxddit.com"
private const val FIXED_UP_TIKTOK_URL = "vxtiktok.com/"

private const val ISAAC_ID = 378213328570417154
private const val FRAN_ID = 651163679814844467
private const val GUTI_ID = 899918332965298176

private const val FRANCE = "francia"
private const val SPAIN = "españa"
private val MEXICO = Pattern.compile("m[eé]xico", Pattern.CASE_INSENSITIVE).toRegex()

private const val TAG = "MessageProcessorService"

class MessageProcessorService(
    appDispatchers: AppDispatchers,
    private val localizationService: LocalizationService
) {
    private val coroutineScope = createCoroutineScope(TAG, appDispatchers)
    private val twitterLinks = listOf(
        "https://x.com/status/",
        "https://www.x.com/status/",
        "https://twitter.com/status/",
        "https://www.twitter.com/status/"
    )
    private val instagramStartLinks = listOf(
        "https://instagram.com",
        "https://www.instagram.com"
    )
    private val instagramStatusLinks = listOf(
        "/p/",
        "/tv/",
        "/reel/",
        "/reels/",
        "/stories/"
    )
    private val instagramLinks = instagramStartLinks.flatMap { start ->
        instagramStatusLinks.map { status ->
            start + status
        }
    }
    private val redditLinks = listOf(
        "https://www.reddit.com",
        "https://reddit.com"
    )
    private val tiktokLinks = listOf(
        "https://www.tiktok.com",
        "https://tiktok.com",
        "https://vm.tiktok.com",
        "https://vt.tiktok.com"
    )
    private val linksToProcess = listOf(twitterLinks, instagramLinks, redditLinks, tiktokLinks).flatten()

    fun processReactions(message: Message) {
        coroutineScope.launch {
            val reactions = when {
                message.author?.id == Snowflake(ISAAC_ID) -> listOf("🍆")
                message.author?.id == Snowflake(FRAN_ID) -> listOf("😢")
                message.author?.id == Snowflake(GUTI_ID) -> listOf("😭")
                message.content.contains(FRANCE, ignoreCase = true) -> listOf("🇫🇷", "🥖", "🥐", "🍷")
                message.content.contains(SPAIN, ignoreCase = true) -> listOf(
                    "🆙",
                    "🇪🇸",
                    "❤️‍🔥",
                    "💃",
                    "🥘",
                    "🏖️",
                    "🛌",
                    "🇪🇦"
                )

                message.content.contains(MEXICO) -> listOf("🇲🇽", "🌯", "🌮", "🫔")
                else -> emptyList()
            }
            reactions.forEach {
                message.addReaction(ReactionEmoji.Unicode(it))
            }
        }
    }

    fun processMessage(message: Message) {
        if (shouldBeProcessed(message.content)) {
            coroutineScope.launch {
                val locale = message.getGuildLocale()
                val processedMessage = getProcessedMessage(message.author?.mention, locale, message.content)
                message.delete()
                message.channel.createMessage(processedMessage)
            }
        }
    }

    private fun shouldBeProcessed(content: String): Boolean = linksToProcess.any { content.contains(it) }

    private fun getProcessedMessage(author: String?, locale: Locale, content: String): String = when {
        twitterLinks.any { content.contains(it) } -> getGenericProcessedMessage(
            author = author,
            content = content,
            links = twitterLinks,
            fixedUpUrl = FIXED_UP_TWITTER_URL,
            locale = locale
        )

        instagramLinks.any { content.contains(it) } -> getGenericProcessedMessage(
            author = author,
            content = content,
            links = instagramStartLinks,
            fixedUpUrl = FIXED_UP_INSTAGRAM_URL,
            locale = locale
        )

        redditLinks.any { content.contains(it) } -> getGenericProcessedMessage(
            author = author,
            content = content,
            links = redditLinks,
            fixedUpUrl = FIXED_UP_REDDIT_URL,
            locale = locale
        )

        tiktokLinks.any { content.contains(it) } -> getTikTokProcessedMessage(
            author = author,
            content = content,
            locale = locale
        )

        else -> localizationService.getStringFormat(
            key = LocalizationKeys.MESSAGE_PROCESSOR_INVALID_LINK,
            locale = locale
        )
    }

    private fun getGenericProcessedMessage(
        author: String?,
        content: String,
        links: List<String>,
        fixedUpUrl: String,
        locale: Locale
    ): String {
        val fixedMessage = content.replace(links.asRegex(), fixedUpUrl)
        return getFixedUpMessage(author, fixedMessage, locale)
    }

    private fun getTikTokProcessedMessage(
        author: String?,
        content: String,
        locale: Locale
    ): String = getFixedUpMessage(author, content.replace("tiktok.com/", FIXED_UP_TIKTOK_URL), locale)

    private fun getFixedUpMessage(author: String?, fixedMessage: String, locale: Locale) =
        localizationService.getStringFormat(
            key = LocalizationKeys.FIXED_UP_LINK,
            locale = locale,
            arguments = arrayOf(author.orEmpty(), fixedMessage)
        )
}
