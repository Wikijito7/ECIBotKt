package es.wokis.data.flowery

import es.wokis.es.wokis.data.flowery.FloweryVoice
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonIgnoreUnknownKeys

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonIgnoreUnknownKeys
data class FloweryVoices(
    val voices: List<FloweryVoice>
)
