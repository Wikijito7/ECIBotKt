package utils

import es.wokis.utils.Log
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.slf4j.Logger

class LogTest {

    companion object {
        private val logger: Logger = mockk()

        @JvmStatic
        @BeforeAll
        fun setUp() {
            Log.setLogger(logger)
        }
    }

    @Test
    fun `When debug is called Then log debug`() {
        // Given
        val message = "Manolete"
        justRun { logger.debug(any()) }

        // When
        Log.debug(message)

        // Then
        verify(exactly = 1) {
            logger.debug(message)
        }
    }

    @Test
    fun `When info is called Then log info`() {
        // Given
        val message = "Manolete"
        justRun { logger.info(any()) }

        // When
        Log.info(message)

        // Then
        verify(exactly = 1) {
            logger.info(message)
        }
    }

    @Test
    fun `When warn is called Then log warn`() {
        // Given
        val message = "Manolete"
        justRun { logger.warn(any()) }

        // When
        Log.warning(message)

        // Then
        verify(exactly = 1) {
            logger.warn(message)
        }
    }

    @Test
    fun `When error is called Then log error`() {
        // Given
        val message = "Manolete"
        justRun { logger.error(any()) }

        // When
        Log.error(message)

        // Then
        verify(exactly = 1) {
            logger.error(message)
        }
    }

    @Test
    fun `When error is called with throwable Then log error message with exception`() {
        // Given
        val message = "Manolete"
        val exception = IllegalArgumentException("test")
        val expected = message + exception.stackTraceToString()
        justRun { logger.error(any()) }

        // When
        Log.error(message, exception)

        // Then
        verify(exactly = 1) {
            logger.error(expected)
        }
    }
}
