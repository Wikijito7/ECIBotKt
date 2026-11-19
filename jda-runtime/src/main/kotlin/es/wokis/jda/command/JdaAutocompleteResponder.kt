package es.wokis.jda.command

import es.wokis.commands.neutral.AutocompleteResponder
import es.wokis.jda.awaitRestActionCompletion
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent
import net.dv8tion.jda.api.interactions.commands.Command

/** Gateway autocomplete choice limit. */
private const val MAX_CHOICES = 25

/** Narrow seam around the REST operation an autocomplete responder needs. */
internal interface AutocompleteResponseDelegate {

    suspend fun replyChoices(suggestions: List<String>)
}

/**
 * JDA implementation of the neutral autocomplete responder.
 *
 * A mutex enforces at most one reply per interaction, and the gateway's 25-choice limit is applied
 * defensively so a handler cannot make the whole suggestion list fail.
 */
class JdaAutocompleteResponder internal constructor(
    private val delegate: AutocompleteResponseDelegate,
) : AutocompleteResponder {

    constructor(event: CommandAutoCompleteInteractionEvent) : this(JdaAutocompleteResponseDelegate(event))

    private val mutex = Mutex()
    private var replied = false

    override suspend fun respond(suggestions: List<String>) {
        mutex.withLock {
            check(!replied) { "The autocomplete interaction has already been answered" }
            delegate.replyChoices(suggestions.take(MAX_CHOICES))
            replied = true
        }
    }
}

/** [AutocompleteResponseDelegate] backed by a real JDA autocomplete event. */
internal class JdaAutocompleteResponseDelegate(
    private val event: CommandAutoCompleteInteractionEvent,
) : AutocompleteResponseDelegate {

    override suspend fun replyChoices(suggestions: List<String>) {
        event.replyChoices(suggestions.map { Command.Choice(it, it) }).awaitRestActionCompletion()
    }
}
