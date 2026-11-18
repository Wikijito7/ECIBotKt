package es.wokis.message

/** Interactive button description; [emoji] is a unicode emoji when present. */
data class ButtonView(
    val componentId: ComponentId,
    val label: String,
    val emoji: String? = null,
    val style: ButtonStyle = ButtonStyle.SECONDARY,
    val disabled: Boolean = false,
)
