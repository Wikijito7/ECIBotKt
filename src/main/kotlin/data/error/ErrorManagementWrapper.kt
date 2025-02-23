package es.wokis.data.error

import es.wokis.data.response.ErrorType
import es.wokis.data.response.RemoteResponse
import io.ktor.client.plugins.*
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.*
import kotlin.coroutines.cancellation.CancellationException

object ErrorManagementWrapper {

    suspend fun <T> wrap(block: suspend () -> T): RemoteResponse<T> =
        try {
            RemoteResponse.Success(block())
        } catch (exc: CancellationException) {
            // If is a CancellationException, we throw it as it's necessary for the framework
            // in order to remove the coroutine.
            throw exc
        } catch (exc: Exception) {
            manageError(exc)
        }

    private fun <T> manageError(exc: Exception): RemoteResponse.Error<T> = RemoteResponse.Error(
        when (exc) {
            is RedirectResponseException -> ErrorType.ServerError(exc.response.status.value, exc.message)

            is ClientRequestException -> ErrorType.RequestError(exc.response.status.value, exc.message)

            is ServerResponseException -> ErrorType.ServerError(exc.response.status.value, exc.message)

            is SocketTimeoutException, is UnknownHostException, is ConnectException -> ErrorType.NoConnectionError(
                "No connection available"
            )

            is IllegalFormatException -> ErrorType.DataParseError("Error parsing data")

            else -> ErrorType.UnknownError(exc, "Unknown error")
        }
    )
}
