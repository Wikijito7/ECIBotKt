package es.wokis.services.radio

import es.wokis.data.error.ErrorManagementWrapper
import es.wokis.data.radio.RadioDTO
import es.wokis.data.radio.RadioPageDTO
import es.wokis.data.response.RemoteResponse
import es.wokis.services.config.ConfigService
import es.wokis.services.lavaplayer.GuildLavaPlayerService
import es.wokis.utils.asEncodedUrl
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import java.net.URI

private const val PRE_ECIBOT_ENDPOINT = "https://pre-ecibot.wokis.es/"
private const val PRO_ECIBOT_ENDPOINT = "https://ecibot.wokis.es/"

class RadioService(
    private val httpClient: HttpClient,
    private val configService: ConfigService
) {

    suspend fun findRadio(radioName: String, guildLavaPlayerService: GuildLavaPlayerService): Boolean {
        val encodedName = radioName.dropLast(1)
        searchRadio(encodedName).let {
            (it as? RemoteResponse.Success)?.data?.firstOrNull()?.let { radio ->
                guildLavaPlayerService.playRadio(
                    radioName = radio.radioName,
                    radioUrl = radio.url,
                    customFavicon = radio.thumbnailUrl
                )
                return true
            }
            return false
        }
    }

    suspend fun searchRadio(prompt: String): RemoteResponse<List<RadioDTO>> = ErrorManagementWrapper.wrap {
        val encodedPrompt = prompt.asEncodedUrl()
        httpClient.get(
            urlString = getUrlNormalized("${getBaseEndpoint()}/radio/find/name/$encodedPrompt")
        ).body<List<RadioDTO>>()
    }

    suspend fun searchRadioPaged(prompt: String, page: Int): RemoteResponse<RadioPageDTO> = ErrorManagementWrapper.wrap {
        val encodedPrompt = prompt.asEncodedUrl()
        httpClient.get(
            urlString = getUrlNormalized("${getBaseEndpoint()}/radio/find/name/$encodedPrompt")
        ).body<RadioPageDTO>()
    }

    suspend fun getRadioList(page: Int): RemoteResponse<RadioPageDTO> = ErrorManagementWrapper.wrap {
        httpClient.get(
            urlString = getUrlNormalized("${getBaseEndpoint()}/radio/page/$page")
        ).body<RadioPageDTO>()
    }

    private fun getBaseEndpoint() =
        getUrlNormalized("${if (configService.config.debug) PRE_ECIBOT_ENDPOINT else PRO_ECIBOT_ENDPOINT}/api")

    private fun getUrlNormalized(url: String): String = URI(url).normalize().toString()
}
