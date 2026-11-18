package es.wokis.message

/** Gateway-neutral embed description; [color] is the raw RGB integer. */
data class EmbedView(
    val title: String? = null,
    val description: String? = null,
    val color: Int = 0,
    val thumbnailUrl: String? = null,
    val fields: List<EmbedFieldView> = emptyList(),
    val footer: String? = null,
)
