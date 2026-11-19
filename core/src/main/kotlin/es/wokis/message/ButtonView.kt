package es.wokis.message

/**
 * Interactive button description; [emoji] is a unicode emoji when present.
 *
 * [payload] is optional component state carried on the wire as `componentId::payload`, the same
 * shape Kord uses for stateful buttons.
 */
data class ButtonView(
    val componentId: ComponentId,
    val label: String,
    val emoji: String? = null,
    val style: ButtonStyle = ButtonStyle.SECONDARY,
    val disabled: Boolean = false,
    val payload: String? = null,
)
