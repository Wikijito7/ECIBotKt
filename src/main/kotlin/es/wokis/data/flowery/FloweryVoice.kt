@file:OptIn(ExperimentalSerializationApi::class)

package es.wokis.es.wokis.data.flowery

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonIgnoreUnknownKeys

@Serializable
@JsonIgnoreUnknownKeys
data class FloweryVoice(
    val id: String,
    val language: FloweryVoiceLanguage
)

@Serializable
@JsonIgnoreUnknownKeys
data class FloweryVoiceLanguage(
    val code: String
)
