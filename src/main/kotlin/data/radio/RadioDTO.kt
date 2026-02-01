package es.wokis.data.radio

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonIgnoreUnknownKeys

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonIgnoreUnknownKeys
data class RadioDTO(
    @SerialName("name")
    val radioName: String,
    @SerialName("url")
    val url: String,
    @SerialName("favicon")
    val thumbnailUrl: String,
    @SerialName("countrycode")
    val countryCode: String
)
