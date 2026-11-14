package es.wokis.repositories.locale

import kotlinx.serialization.json.Json

/**
 * JSON codec shared by every runtime that persists guild locales.
 *
 * Keeping one instance guarantees that the Kord and JDA runtimes read and write the same legacy
 * format without drifting in their encoder settings.
 */
object LocaleJson {
    val json: Json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }
}
