package es.wokis.utils

import org.slf4j.Logger

object Log {
    private var logger: Logger? = null

    fun setLogger(logger: Logger) {
        this.logger = logger
    }

    fun debug(message: String) {
        logger?.debug(message)
    }

    fun info(message: String) {
        logger?.info(message)
    }

    fun warning(message: String) {
        logger?.warn(message)
    }

    fun error(message: String, exception: Throwable? = null) {
        val messageWithError = exception?.let { message + exception.stackTraceToString() } ?: message
        logger?.error(messageWithError)
    }
}
