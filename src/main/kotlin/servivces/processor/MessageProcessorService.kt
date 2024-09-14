package es.wokis.servivces.processor

import dev.kord.common.entity.Snowflake
import dev.kord.core.entity.Message
import dev.kord.core.entity.ReactionEmoji
import es.wokis.dispatchers.AppDispatchers
import es.wokis.utils.asRegex
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.coroutines.CoroutineContext

private const val FIXED_UP_TWITTER_URL = "https://fixupx"
private const val FIXED_UP_INSTAGRAM_URL = "https://ddinstagram"
private const val FIXED_UP_REDDIT_URL = "https://rxddit"
private const val FIXED_UP_TIKTOK_URL = "https://tnktok"

private const val ISAAC_ID = 378213328570417154
private const val FRAN_ID = 651163679814844467
private const val GUTI_ID = 899918332965298176

private const val FRANCE = "francia"
private const val SPAIN = "españa"
private const val MEXICO = "mexico"

class MessageProcessorService(
    private val appDispatchers: AppDispatchers
) {
    private val coroutineScope = CoroutineScope(SupervisorJob() + appDispatchers.io + coroutineExceptionHandler())
    private val twitterLinks = listOf(
        "https://x.com",
        "https://www.x.com",
        "https://twitter.com",
        "https://www.twitter.com"
    )
    private val instagramLinks = listOf(
        "https://instagram.com",
        "https://www.instagram.com"
    )
    private val redditLinks = listOf(
        "https://www.reddit.com",
        "https://reddit.com"
    )
    private val tiktokLinks = listOf(
        "https://www.tiktok.com",
        "https://tiktok.com",
        "https://vm.tiktok.com"
    )
    private val linksToProcess = listOf(twitterLinks, instagramLinks, redditLinks, tiktokLinks).flatten()

    fun processReactions(message: Message) {
        coroutineScope.launch(appDispatchers.io) {
            val reactions = when {
                message.author?.id == Snowflake(ISAAC_ID) -> listOf("🍆")
                message.author?.id == Snowflake(FRAN_ID) -> listOf("😢")
                message.author?.id == Snowflake(GUTI_ID) -> listOf("😭")
                message.content.contains(FRANCE) -> listOf("🇫🇷", "🥖", "🥐", "🍷")
                message.content.contains(SPAIN) -> listOf("🆙", "🇪🇸", "❤️‍🔥", "💃", "🥘", "🏖️", "🛌", "🇪🇦")
                message.content.contains(MEXICO) -> listOf("🇲🇽", "🌯", "🌮", "🫔")
                else -> emptyList()
            }
            reactions.takeUnless { it.isEmpty() }?.let {
                it.map { reaction -> ReactionEmoji.Unicode(reaction) }.forEach { reaction ->
                    message.addReaction(reaction)
                }
            }
        }
    }

    fun processMessage(message: Message) {
        if (shouldBeProcessed(message.content)) {
            val processedMessage = getProcessedMessage(message.author?.mention, message.content)
            coroutineScope.launch(appDispatchers.io) {
                message.delete()
                message.channel.createMessage(processedMessage)
            }
        }
    }

    private fun shouldBeProcessed(content: String): Boolean = linksToProcess.any { content.contains(it) }

    private fun getProcessedMessage(author: String?, content: String): String = when {
        twitterLinks.any { content.contains(it) } -> getGenericProcessedMessage(
            author = author,
            content = content,
            links = twitterLinks,
            fixedUpUrl = FIXED_UP_TWITTER_URL
        )

        instagramLinks.any { content.contains(it) } -> getGenericProcessedMessage(
            author = author,
            content = content,
            links = instagramLinks,
            fixedUpUrl = FIXED_UP_INSTAGRAM_URL
        )

        redditLinks.any { content.contains(it) } -> getGenericProcessedMessage(
            author = author,
            content = content,
            links = redditLinks,
            fixedUpUrl = FIXED_UP_REDDIT_URL
        )

        tiktokLinks.any { content.contains(it) } -> getGenericProcessedMessage(
            author = author,
            content = content,
            links = tiktokLinks,
            fixedUpUrl = FIXED_UP_TIKTOK_URL
        )

        else -> "I've received an invalid link. The original one was: $content."
    }

    private fun getGenericProcessedMessage(
        author: String?,
        content: String,
        links: List<String>,
        fixedUpUrl: String
    ): String {
        val fixedMessage = content.replace(links.asRegex(), fixedUpUrl)
        return "Post enviado por $author con el enlace arreglado:\n$fixedMessage"
    }

    private fun coroutineExceptionHandler(): CoroutineContext = CoroutineExceptionHandler { _, throwable ->
        Logger.getLogger("ecibotkt").log(Level.SEVERE, "There's been an error on MessageProcessorService.", throwable)
    }
}
