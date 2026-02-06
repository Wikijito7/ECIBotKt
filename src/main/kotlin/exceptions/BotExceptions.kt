package es.wokis.exceptions

import es.wokis.data.response.ErrorType

/**
 * Sealed base class for all bot-related exceptions.
 * All custom exceptions must extend one of the sealed subclasses.
 * This allows for exhaustive when expressions and better type safety.
 */
sealed class BotException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause) {

    /**
     * Sealed class for user-facing exceptions.
     * These are expected errors that should be displayed to users with localization.
     */
    sealed class UserException(
        val localizationKey: String,
        vararg val args: Any,
        message: String = "User error: $localizationKey"
    ) : BotException(message) {

        /**
         * Thrown when user is not connected to a voice channel
         */
        class NotInVoiceChannelException(
            localizationKey: String = "error_not_in_voice_channel",
            vararg args: Any
        ) : UserException(localizationKey, *args)

        /**
         * Thrown when command is executed outside a guild (server)
         */
        class NotInGuildException(
            localizationKey: String = "error_not_in_guild",
            vararg args: Any
        ) : UserException(localizationKey, *args)

        /**
         * Thrown when required content/argument is not provided
         */
        class NoContentProvidedException(
            localizationKey: String = "error_no_content_provided",
            vararg args: Any
        ) : UserException(localizationKey, *args)

        /**
         * Thrown when user is not in a text channel
         */
        class NotInTextChannelException(
            localizationKey: String = "error_not_in_text_channel",
            vararg args: Any
        ) : UserException(localizationKey, *args)

        /**
         * Thrown when queue is empty and an operation requires items in queue
         */
        class EmptyQueueException(
            localizationKey: String = "error_empty_queue",
            vararg args: Any
        ) : UserException(localizationKey, *args)

        /**
         * Thrown when audio playback fails
         */
        class AudioPlaybackException(
            localizationKey: String = "error_audio_playback",
            vararg args: Any
        ) : UserException(localizationKey, *args)

        /**
         * Thrown when a sound file is not found
         */
        class SoundNotFoundException(
            localizationKey: String = "error_sound_not_found",
            vararg args: Any
        ) : UserException(localizationKey, *args)

        /**
         * Thrown when track is not found in queue
         */
        class TrackNotFoundException(
            localizationKey: String = "error_track_not_found",
            vararg args: Any
        ) : UserException(localizationKey, *args)

        /**
         * Thrown when user is not connected to same voice channel as bot
         */
        class NotInSameVoiceChannelException(
            localizationKey: String = "error_not_in_same_voice_channel",
            vararg args: Any
        ) : UserException(localizationKey, *args)

        /**
         * Thrown when there is no voice connection established
         */
        class NoVoiceConnectionException(
            localizationKey: String = "error_no_voice_connection",
            vararg args: Any
        ) : UserException(localizationKey, *args)

        /**
         * Thrown when trying to skip but no track is playing
         */
        class NoTrackPlayingException(
            localizationKey: String = "error_no_track_playing",
            vararg args: Any
        ) : UserException(localizationKey, *args)
    }

    /**
     * Sealed class for system/internal exceptions.
     * These are unexpected errors that need investigation.
     */
    sealed class SystemException(
        message: String,
        val originalException: Throwable? = null
    ) : BotException(message, originalException) {

        /**
         * Thrown when queue operation fails unexpectedly
         */
        class QueueOperationException(
            message: String,
            originalException: Throwable? = null
        ) : SystemException(message, originalException)

        /**
         * Thrown when voice channel operation fails
         */
        class VoiceChannelException(
            message: String,
            originalException: Throwable? = null
        ) : SystemException(message, originalException)

        /**
         * Thrown when an unexpected error occurs during command execution
         */
        class CommandExecutionException(
            message: String,
            originalException: Throwable? = null
        ) : SystemException(message, originalException)
    }

    /**
     * Sealed class for external API exceptions.
     * These wrap RemoteResponse.Error types for command-level handling.
     */
    sealed class APIException(
        val errorType: ErrorType,
        message: String = "API error: ${errorType.errorMessage}"
    ) : BotException(message) {

        /**
         * Thrown when network connection fails
         */
        class NetworkConnectionException(
            errorType: ErrorType,
            message: String = "Network error: ${errorType.errorMessage}"
        ) : APIException(errorType, message)

        /**
         * Thrown when server returns an error
         */
        class ServerErrorException(
            errorType: ErrorType,
            message: String = "Server error: ${errorType.errorMessage}"
        ) : APIException(errorType, message)

        /**
         * Thrown when request is invalid
         */
        class RequestErrorException(
            errorType: ErrorType,
            message: String = "Request error: ${errorType.errorMessage}"
        ) : APIException(errorType, message)

        /**
         * Thrown when data parsing fails
         */
        class DataParseException(
            errorType: ErrorType,
            message: String = "Data parse error: ${errorType.errorMessage}"
        ) : APIException(errorType, message)

        /**
         * Generic API exception for unknown or custom error types.
         * Used as a fallback when no specific exception type matches.
         */
        class GenericAPIException(
            errorType: ErrorType,
            message: String = "API error: ${errorType.errorMessage}"
        ) : APIException(errorType, message)
    }
}

/**
 * Factory function to convert ErrorType to appropriate APIException.
 * This is placed in the same package to access the sealed class constructors.
 */
fun ErrorType.toException(): BotException.APIException = when (this) {
    is ErrorType.NoConnectionError -> BotException.APIException.NetworkConnectionException(this)
    is ErrorType.ServerError -> BotException.APIException.ServerErrorException(this)
    is ErrorType.RequestError -> BotException.APIException.RequestErrorException(this)
    is ErrorType.DataParseError -> BotException.APIException.DataParseException(this)
    is ErrorType.UnknownError -> BotException.APIException.GenericAPIException(this)
    is ErrorType.CustomError -> BotException.APIException.GenericAPIException(this)
}
