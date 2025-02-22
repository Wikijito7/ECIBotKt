package es.wokis.data.response

sealed class RemoteResponse<T>(val data: T?, val message: String?) {
    class Success<T>(data: T?) : RemoteResponse<T>(data, null)

    class Error<T>(val error: ErrorType, data: T? = null) : RemoteResponse<T>(data, error.errorMessage)
}

fun <T, R> RemoteResponse<T>.map(transform: (T) -> R): RemoteResponse<R> = when (this) {
    is RemoteResponse.Error -> RemoteResponse.Error(error, this.data?.let { transform(it) })
    is RemoteResponse.Success -> RemoteResponse.Success(this.data?.let { transform(it) })
}
