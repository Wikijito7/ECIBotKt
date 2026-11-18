package es.wokis.message

/** Gateway-neutral message description: optional text, one optional embed and action rows. */
data class MessageView(
    val content: String? = null,
    val embed: EmbedView? = null,
    val rows: List<ActionRowView> = emptyList(),
) {

    /** True when the view carries nothing beyond plain text, so adapters can keep the text path. */
    val isTextOnly: Boolean get() = embed == null && rows.isEmpty()
}
