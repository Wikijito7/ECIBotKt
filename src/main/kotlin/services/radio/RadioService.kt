package es.wokis.services.radio

import es.wokis.data.error.ErrorManagementWrapper
import es.wokis.data.radio.RadioCountryCodeDTO
import es.wokis.data.radio.RadioDTO
import es.wokis.data.radio.RadioPageDTO
import es.wokis.data.response.RemoteResponse
import es.wokis.services.config.ConfigService
import es.wokis.utils.asEncodedUrl
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import java.net.URI

private const val PRE_ECIBOT_ENDPOINT = "https://pre-ecibot.wokis.es/"
private const val PRO_ECIBOT_ENDPOINT = "https://ecibot.wokis.es/"
private const val CACHE_EXPIRATION_MS = 3600000L // 1 hour in milliseconds

class RadioService(
    private val httpClient: HttpClient,
    private val configService: ConfigService
) {
    // In-memory cache for country codes
    private var cachedCountryCodes: RadioCountryCodeDTO? = null
    private var cacheTimestamp: Long = 0

    suspend fun findRadio(radioName: String): RadioDTO? {
        val encodedName = radioName.dropLast(1)
        return searchRadio(encodedName).let {
            (it as? RemoteResponse.Success)?.data?.firstOrNull()
        }
    }

    suspend fun searchRadio(prompt: String): RemoteResponse<List<RadioDTO>> = ErrorManagementWrapper.wrap {
        val encodedPrompt = prompt.asEncodedUrl()
        httpClient.get(
            urlString = getUrlNormalized("${getBaseEndpoint()}/radio/find/name/$encodedPrompt")
        ).body<List<RadioDTO>>()
    }

    suspend fun searchRadioByNamePaged(prompt: String, page: Int): RemoteResponse<RadioPageDTO> = ErrorManagementWrapper.wrap {
        val encodedPrompt = prompt.asEncodedUrl()
        httpClient.get(
            urlString = getUrlNormalized("${getBaseEndpoint()}/radio/find/name/$encodedPrompt/page/$page")
        ).body<RadioPageDTO>()
    }

    suspend fun searchRadioByCountryCodePaged(prompt: String, page: Int): RemoteResponse<RadioPageDTO> = ErrorManagementWrapper.wrap {
        val encodedPrompt = prompt.asEncodedUrl()
        httpClient.get(
            urlString = getUrlNormalized("${getBaseEndpoint()}/radio/find/countrycode/$encodedPrompt/page/$page")
        ).body<RadioPageDTO>()
    }

    suspend fun getRadioList(page: Int): RemoteResponse<RadioPageDTO> = ErrorManagementWrapper.wrap {
        httpClient.get(
            urlString = getUrlNormalized("${getBaseEndpoint()}/radio/page/$page")
        ).body<RadioPageDTO>()
    }

    suspend fun getRandomRadio(): RemoteResponse<RadioDTO> = ErrorManagementWrapper.wrap {
        httpClient.get(
            urlString = getUrlNormalized("${getBaseEndpoint()}/radio/random")
        ).body<RadioDTO>()
    }

    suspend fun getCountryCodes(): RemoteResponse<RadioCountryCodeDTO> {
        // Check if cache is valid (not expired and not null)
        val currentTime = System.currentTimeMillis()
        cachedCountryCodes?.let {
            if ((currentTime - cacheTimestamp) < CACHE_EXPIRATION_MS) {
                return RemoteResponse.Success(it)
            }
        }

        // Fetch from API and update cache
        return ErrorManagementWrapper.wrap {
            httpClient.get(
                urlString = getUrlNormalized("${getBaseEndpoint()}/radio/countrycodes")
            ).body<RadioCountryCodeDTO>().also {
                cachedCountryCodes = it
                cacheTimestamp = currentTime
            }
        }
    }

    private fun getBaseEndpoint() =
        getUrlNormalized("${if (configService.config.debug) PRE_ECIBOT_ENDPOINT else PRO_ECIBOT_ENDPOINT}/api")

    private fun getUrlNormalized(url: String): String = URI(url).normalize().toString()
}
