package es.wokis.services.ask

interface AIProvider {
    suspend fun ask(prompt: String, model: String): String
}
