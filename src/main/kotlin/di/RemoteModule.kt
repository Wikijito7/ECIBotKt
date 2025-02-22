package es.wokis.di

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.auth.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.plugins.resources.*
import io.ktor.serialization.kotlinx.json.*
import org.koin.dsl.module

private const val SOCKET_TIMEOUT_MILLIS = 20000L
private const val CONNECT_TIMEOUT_MILLIS = 20000L
private const val REQUEST_TIMEOUT_MILLIS = 20000L

val remoteModule = module {
    single<HttpClient> {
        HttpClient(CIO) {
            BrowserUserAgent()
            install(Logging) {
                logger = Logger.SIMPLE
                level = LogLevel.BODY
            }
            install(ContentNegotiation) {
                json()
            }
            install(Resources)
            install(Auth)
            install(HttpTimeout) {
                socketTimeoutMillis = SOCKET_TIMEOUT_MILLIS
                connectTimeoutMillis = CONNECT_TIMEOUT_MILLIS
                requestTimeoutMillis = REQUEST_TIMEOUT_MILLIS
            }
            Charsets {
                register(Charsets.UTF_8)
            }
            expectSuccess = true
        }
    }
}
