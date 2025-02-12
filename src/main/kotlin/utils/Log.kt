package es.wokis.utils

import es.wokis.bot.Bot
import org.slf4j.LoggerFactory
import java.lang.Exception

object Log {
    private val logger = LoggerFactory.getLogger(Bot::class.java)

    fun debug(message: String) {
        logger.debug(message)
    }

    fun info(message: String) {
        logger.info(message)
    }

    fun warning(message: String) {
        logger.warn(message)
    }

    fun error(message: String, exception: Throwable? = null) {
        val messageWithError = exception?.let { message + exception.stackTraceToString() } ?: message
        logger.error(messageWithError)
    }
}
