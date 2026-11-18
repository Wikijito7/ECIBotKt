package es.wokis.message

/** One embed field. */
data class EmbedFieldView(
    val name: String,
    val value: String,
    val inline: Boolean = false,
)
