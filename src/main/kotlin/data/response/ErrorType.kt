package es.wokis.data.response

sealed class ErrorType(val errorMessage: String) {
    class NoConnectionError(errorMessage: String) : ErrorType(errorMessage)
    class RequestError(val httpCode: Int, errorMessage: String) : ErrorType(errorMessage)
    class ServerError(val httpCode: Int, errorMessage: String) : ErrorType(errorMessage)
    class DataParseError(errorMessage: String) : ErrorType(errorMessage)
    class UnknownError(val exception: Throwable, errorMessage: String) : ErrorType(errorMessage)

    open class CustomError(errorMessage: String) : ErrorType(errorMessage)
}
