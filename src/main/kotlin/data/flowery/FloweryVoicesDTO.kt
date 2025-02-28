package es.wokis.data.flowery

import es.wokis.es.wokis.data.flowery.FloweryVoiceDTO
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonIgnoreUnknownKeys

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonIgnoreUnknownKeys
data class FloweryVoicesDTO(
    val voices: List<FloweryVoiceDTO>
)
