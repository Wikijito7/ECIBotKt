package exceptions

import es.wokis.data.response.ErrorType
import es.wokis.exceptions.BotException
import es.wokis.exceptions.toException
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BotExceptionsTest {

    //region UserException Tests

    @Test
    fun `Given NotInVoiceChannelException When created Then has correct properties`() {
        // Given
        val exception = BotException.UserException.NotInVoiceChannelException()

        // Then
        assertEquals("error_not_in_voice_channel", exception.localizationKey)
        assertTrue(exception.message?.contains("error_not_in_voice_channel") == true)
    }

    @Test
    fun `Given NotInVoiceChannelException with custom key When created Then uses custom key`() {
        // Given
        val customKey = "custom_key"
        val exception = BotException.UserException.NotInVoiceChannelException(localizationKey = customKey)

        // Then
        assertEquals(customKey, exception.localizationKey)
    }

    @Test
    fun `Given NotInGuildException When created Then has correct properties`() {
        // Given
        val exception = BotException.UserException.NotInGuildException()

        // Then
        assertEquals("error_not_in_guild", exception.localizationKey)
    }

    @Test
    fun `Given NoContentProvidedException When created Then has correct properties`() {
        // Given
        val exception = BotException.UserException.NoContentProvidedException()

        // Then
        assertEquals("error_no_content_provided", exception.localizationKey)
    }

    @Test
    fun `Given NotInTextChannelException When created Then has correct properties`() {
        // Given
        val exception = BotException.UserException.NotInTextChannelException()

        // Then
        assertEquals("error_not_in_text_channel", exception.localizationKey)
    }

    @Test
    fun `Given EmptyQueueException When created Then has correct properties`() {
        // Given
        val exception = BotException.UserException.EmptyQueueException()

        // Then
        assertEquals("error_empty_queue", exception.localizationKey)
    }

    @Test
    fun `Given AudioPlaybackException When created Then has correct properties`() {
        // Given
        val exception = BotException.UserException.AudioPlaybackException()

        // Then
        assertEquals("error_audio_playback", exception.localizationKey)
    }

    @Test
    fun `Given SoundNotFoundException When created Then has correct properties`() {
        // Given
        val exception = BotException.UserException.SoundNotFoundException()

        // Then
        assertEquals("error_sound_not_found", exception.localizationKey)
    }

    @Test
    fun `Given TrackNotFoundException When created Then has correct properties`() {
        // Given
        val exception = BotException.UserException.TrackNotFoundException()

        // Then
        assertEquals("error_track_not_found", exception.localizationKey)
    }

    @Test
    fun `Given NotInSameVoiceChannelException When created Then has correct properties`() {
        // Given
        val exception = BotException.UserException.NotInSameVoiceChannelException()

        // Then
        assertEquals("error_not_in_same_voice_channel", exception.localizationKey)
    }

    @Test
    fun `Given NoVoiceConnectionException When created Then has correct properties`() {
        // Given
        val exception = BotException.UserException.NoVoiceConnectionException()

        // Then
        assertEquals("error_no_voice_connection", exception.localizationKey)
    }

    @Test
    fun `Given NoTrackPlayingException When created Then has correct properties`() {
        // Given
        val exception = BotException.UserException.NoTrackPlayingException()

        // Then
        assertEquals("error_no_track_playing", exception.localizationKey)
    }

    @Test
    fun `Given UserException with args When created Then stores args`() {
        // Given
        val arg1 = "arg1"
        val arg2 = "arg2"
        val exception = BotException.UserException.NotInVoiceChannelException(args = arrayOf(arg1, arg2))

        // Then
        assertEquals(2, exception.args.size)
        assertEquals(arg1, exception.args[0])
        assertEquals(arg2, exception.args[1])
    }

    //endregion

    //region SystemException Tests

    @Test
    fun `Given QueueOperationException When created Then has correct properties`() {
        // Given
        val message = "Queue operation failed"
        val originalException = Exception("Original error")
        val exception = BotException.SystemException.QueueOperationException(message, originalException)

        // Then
        assertEquals(message, exception.message)
        assertEquals(originalException, exception.originalException)
    }

    @Test
    fun `Given QueueOperationException without original exception When created Then has null cause`() {
        // Given
        val message = "Queue operation failed"
        val exception = BotException.SystemException.QueueOperationException(message)

        // Then
        assertEquals(message, exception.message)
        assertEquals(null, exception.originalException)
    }

    @Test
    fun `Given VoiceChannelException When created Then has correct properties`() {
        // Given
        val message = "Voice channel error"
        val originalException = Exception("Original")
        val exception = BotException.SystemException.VoiceChannelException(message, originalException)

        // Then
        assertEquals(message, exception.message)
        assertEquals(originalException, exception.originalException)
    }

    @Test
    fun `Given CommandExecutionException When created Then has correct properties`() {
        // Given
        val message = "Command execution failed"
        val originalException = Exception("Original")
        val exception = BotException.SystemException.CommandExecutionException(message, originalException)

        // Then
        assertEquals(message, exception.message)
        assertEquals(originalException, exception.originalException)
    }

    //endregion

    //region APIException Tests

    @Test
    fun `Given NetworkConnectionException When created Then has correct properties`() {
        // Given
        val errorType = ErrorType.NoConnectionError("No connection")
        val exception = BotException.APIException.NetworkConnectionException(errorType)

        // Then
        assertEquals(errorType, exception.errorType)
        assertTrue(exception.message?.contains("Network error") == true)
    }

    @Test
    fun `Given ServerErrorException When created Then has correct properties`() {
        // Given
        val errorType = ErrorType.ServerError(500, "Server error")
        val exception = BotException.APIException.ServerErrorException(errorType)

        // Then
        assertEquals(errorType, exception.errorType)
        assertTrue(exception.message?.contains("Server error") == true)
    }

    @Test
    fun `Given RequestErrorException When created Then has correct properties`() {
        // Given
        val errorType = ErrorType.RequestError(400, "Bad request")
        val exception = BotException.APIException.RequestErrorException(errorType)

        // Then
        assertEquals(errorType, exception.errorType)
        assertTrue(exception.message?.contains("Request error") == true)
    }

    @Test
    fun `Given DataParseException When created Then has correct properties`() {
        // Given
        val errorType = ErrorType.DataParseError("Parse error")
        val exception = BotException.APIException.DataParseException(errorType)

        // Then
        assertEquals(errorType, exception.errorType)
        assertTrue(exception.message?.contains("Data parse error") == true)
    }

    @Test
    fun `Given GenericAPIException When created Then has correct properties`() {
        // Given
        val errorType = ErrorType.UnknownError(Exception(), "Unknown error")
        val exception = BotException.APIException.GenericAPIException(errorType)

        // Then
        assertEquals(errorType, exception.errorType)
        assertTrue(exception.message?.contains("API error") == true)
    }

    @Test
    fun `Given APIException with custom message When created Then uses custom message`() {
        // Given
        val errorType = ErrorType.UnknownError(Exception(), "Unknown")
        val customMessage = "Custom error message"
        val exception = BotException.APIException.GenericAPIException(errorType, customMessage)

        // Then
        assertEquals(customMessage, exception.message)
    }

    //endregion

    //region toException() Tests

    @Test
    fun `Given NoConnectionError When toException is called Then returns NetworkConnectionException`() {
        // Given
        val errorType = ErrorType.NoConnectionError("No connection")

        // When
        val exception = errorType.toException()

        // Then
        assertTrue(exception is BotException.APIException.NetworkConnectionException)
        assertEquals(errorType, exception.errorType)
    }

    @Test
    fun `Given ServerError When toException is called Then returns ServerErrorException`() {
        // Given
        val errorType = ErrorType.ServerError(500, "Server error")

        // When
        val exception = errorType.toException()

        // Then
        assertTrue(exception is BotException.APIException.ServerErrorException)
        assertEquals(errorType, exception.errorType)
    }

    @Test
    fun `Given RequestError When toException is called Then returns RequestErrorException`() {
        // Given
        val errorType = ErrorType.RequestError(400, "Bad request")

        // When
        val exception = errorType.toException()

        // Then
        assertTrue(exception is BotException.APIException.RequestErrorException)
        assertEquals(errorType, exception.errorType)
    }

    @Test
    fun `Given DataParseError When toException is called Then returns DataParseException`() {
        // Given
        val errorType = ErrorType.DataParseError("Parse error")

        // When
        val exception = errorType.toException()

        // Then
        assertTrue(exception is BotException.APIException.DataParseException)
        assertEquals(errorType, exception.errorType)
    }

    @Test
    fun `Given UnknownError When toException is called Then returns GenericAPIException`() {
        // Given
        val errorType = ErrorType.UnknownError(Exception(), "Unknown")

        // When
        val exception = errorType.toException()

        // Then
        assertTrue(exception is BotException.APIException.GenericAPIException)
        assertEquals(errorType, exception.errorType)
    }

    @Test
    fun `Given CustomError When toException is called Then returns GenericAPIException`() {
        // Given
        val errorType = ErrorType.CustomError("Custom error")

        // When
        val exception = errorType.toException()

        // Then
        assertTrue(exception is BotException.APIException.GenericAPIException)
        assertEquals(errorType, exception.errorType)
    }

    //endregion

    //region Exception Hierarchy Tests

    @Test
    fun `Given UserException When checking inheritance Then extends BotException`() {
        // Given
        val exception = BotException.UserException.NotInVoiceChannelException()

        // Then
        assertTrue(exception is BotException)
        assertTrue(exception is BotException.UserException)
    }

    @Test
    fun `Given SystemException When checking inheritance Then extends BotException`() {
        // Given
        val exception = BotException.SystemException.QueueOperationException("test")

        // Then
        assertTrue(exception is BotException)
        assertTrue(exception is BotException.SystemException)
    }

    @Test
    fun `Given APIException When checking inheritance Then extends BotException`() {
        // Given
        val errorType = ErrorType.UnknownError(Exception(), "test")
        val exception = BotException.APIException.GenericAPIException(errorType)

        // Then
        assertTrue(exception is BotException)
        assertTrue(exception is BotException.APIException)
    }

    @Test
    fun `Given BotException When checking inheritance Then extends RuntimeException`() {
        // Given
        val exception = BotException.UserException.NotInVoiceChannelException()

        // Then
        assertTrue(exception is RuntimeException)
    }

    //endregion
}
