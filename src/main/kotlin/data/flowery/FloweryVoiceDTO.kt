@file:OptIn(ExperimentalSerializationApi::class)

package es.wokis.data.flowery

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonIgnoreUnknownKeys

@Serializable
@JsonIgnoreUnknownKeys
data class FloweryVoiceDTO(
    val id: String,
    val language: FloweryVoiceLanguageDTO
)

@Serializable
@JsonIgnoreUnknownKeys
data class FloweryVoiceLanguageDTO(
    val code: String
)
