package es.wokis.commands.neutral

/**
 * Text-only command response accepted by [CommandResponder].
 *
 * [ephemeral] controls visibility only for an immediate response. After [CommandResponder.defer]
 * the response edits the deferred original message and visibility is fixed by the defer call.
 */
data class CommandResponse(
    val content: String,
    val ephemeral: Boolean = false,
)
