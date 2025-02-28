package es.wokis.domain

import es.wokis.data.error.ErrorManagementWrapper
import es.wokis.data.flowery.FloweryVoicesDTO
import es.wokis.data.response.RemoteResponse
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*

class GetFloweryVoicesUseCase(
    private val httpClient: HttpClient
) : suspend () -> List<String> {

    override suspend fun invoke(): List<String> = ErrorManagementWrapper.wrap {
        httpClient.get("https://api.flowery.pw/v1/tts/voices").body<FloweryVoicesDTO>()
    }.let { response ->
        (response as? RemoteResponse.Success)?.data?.voices?.filter { it.language.code.contains("es-") }?.map { it.id }.orEmpty()
    }
}
