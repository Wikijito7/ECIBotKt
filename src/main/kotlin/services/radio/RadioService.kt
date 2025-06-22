package es.wokis.services.radio

import es.wokis.services.lavaplayer.GuildLavaPlayerService
import io.ktor.client.*

class RadioService(
    private val httpClient: HttpClient,
) {

    fun findRadio(radioName: String, guildLavaPlayerService: GuildLavaPlayerService) {

    }

}
