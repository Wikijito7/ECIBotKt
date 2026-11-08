package es.wokis.commands.neutral

/**
 * Neutral responder for a single command invocation.
 *
 * Contract:
 * - At most one successful [defer] call and at most one successful [respond] call are allowed
 *   per invocation.
 * - A [respond] before [defer] is an immediate response and uses [CommandResponse.ephemeral].
 * - After a successful [defer] the response edits the original (deferred) message. Visibility
 *   is fixed by the [defer] call and [CommandResponse.ephemeral] must not change it.
 * - A second [defer] after a successful [defer] is rejected, as are [defer] after a successful
 *   [respond] and a second [respond] after a successful [respond]. Rejections are programming
 *   errors and must not issue another gateway call.
 * - If a gateway operation fails, implementations restore the previous state and rethrow the
 *   failure, so the same operation can be retried: a failed [defer] may be retried or followed
 *   by an immediate [respond], and a failed [respond] may be retried.
 */
interface CommandResponder {

    /**
     * Acknowledges the invocation without content.
     *
     * @param ephemeral whether the eventual deferred response is ephemeral; this cannot be
     * changed by the later [respond] call.
     */
    suspend fun defer(ephemeral: Boolean = false)

    /**
     * Sends the text response or edits the deferred original when [defer] was called first.
     */
    suspend fun respond(response: CommandResponse)
}
