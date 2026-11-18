package es.wokis.commands.neutral

import es.wokis.message.MessageView

/**
 * Optional capability of a [CommandResponder]: deliver a composed [MessageView] (embed and/or
 * buttons) instead of plain text.
 *
 * It is deliberately separate from [CommandResponder] so text-only adapters keep implementing the
 * bare contract. Adapters that route view-based commands must provide this capability on the same
 * responder instance, so the one-defer/one-response rule still holds per interaction.
 */
interface ViewCommandResponder {

    /** Sends the view, or edits the deferred original when [CommandResponder.defer] came first. */
    suspend fun respond(view: MessageView)
}
