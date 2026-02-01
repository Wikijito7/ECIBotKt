package es.wokis.data.radio

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonIgnoreUnknownKeys

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonIgnoreUnknownKeys
data class RadioPageDTO(
    @SerialName("currentPage")
    val currentPage: Int,
    @SerialName("maxPage")
    val maxPage: Int,
    @SerialName("radios")
    val radios: List<RadioDTO>
)
