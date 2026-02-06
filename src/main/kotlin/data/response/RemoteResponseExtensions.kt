package es.wokis.data.response

import es.wokis.exceptions.toException

/**
 * Extension function to convert RemoteResponse to a value or throw an exception.
 *
 * Usage:
 * ```kotlin
 * val result = ErrorManagementWrapper.wrap { apiCall() }.getOrThrow()
 * ```
 *
 * @return The data if successful
 * @throws es.wokis.exceptions.APIException if the response is an error
 * @throws IllegalStateException if success but data is null
 */
fun <T> RemoteResponse<T>.getOrThrow(): T {
    return when (this) {
        is RemoteResponse.Success -> data ?: throw IllegalStateException("Success response but data is null")
        is RemoteResponse.Error -> throw error.toException()
    }
}

/**
 * Extension function to convert RemoteResponse to a value or return null.
 *
 * Usage:
 * ```kotlin
 * val result = ErrorManagementWrapper.wrap { apiCall() }.getOrNull()
 * ```
 *
 * @return The data if successful, null otherwise
 */
fun <T> RemoteResponse<T>.getOrNull(): T? {
    return when (this) {
        is RemoteResponse.Success -> data
        is RemoteResponse.Error -> null
    }
}

/**
 * Extension function to convert RemoteResponse to a value or return a default.
 *
 * Usage:
 * ```kotlin
 * val result = ErrorManagementWrapper.wrap { apiCall() }.getOrDefault(emptyList())
 * ```
 *
 * @param defaultValue The default value to return if error or data is null
 * @return The data if successful, defaultValue otherwise
 */
fun <T> RemoteResponse<T>.getOrDefault(defaultValue: T): T {
    return when (this) {
        is RemoteResponse.Success -> data ?: defaultValue
        is RemoteResponse.Error -> defaultValue
    }
}
