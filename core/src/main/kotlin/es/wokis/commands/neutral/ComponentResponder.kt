package es.wokis.commands.neutral

import es.wokis.message.MessageView

/** Neutral responder for one component interaction: acknowledge by edit or edit directly. */
interface ComponentResponder {

    /** Acknowledges the interaction as a message update without changing its visible content. */
    suspend fun deferEdit()

    /** Edits the clicked message with [view]; only one edit is allowed per interaction. */
    suspend fun edit(view: MessageView)
}
