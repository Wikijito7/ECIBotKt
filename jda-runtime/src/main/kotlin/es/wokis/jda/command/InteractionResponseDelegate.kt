package es.wokis.jda.command

import es.wokis.jda.awaitRestActionCompletion
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent

/**
 * Narrow seam around the three REST operations a responder needs.
 *
 * Keeping the operations behind a delegate lets the responder state machine be exercised
 * without mocking JDA internals, and keeps every JDA type in this adapter.
 */
internal interface InteractionResponseDelegate {

    suspend fun defer(ephemeral: Boolean)

    suspend fun reply(content: String, ephemeral: Boolean)

    suspend fun editOriginal(content: String)
}

/**
 * [InteractionResponseDelegate] backed by a real JDA slash-command event.
 */
internal class JdaInteractionResponseDelegate(
    private val event: SlashCommandInteractionEvent,
) : InteractionResponseDelegate {

    override suspend fun defer(ephemeral: Boolean) {
        event.deferReply(ephemeral).awaitRestActionCompletion()
    }

    override suspend fun reply(content: String, ephemeral: Boolean) {
        event.reply(content)
            .setEphemeral(ephemeral)
            .awaitRestActionCompletion()
    }

    override suspend fun editOriginal(content: String) {
        event.hook.editOriginal(content).awaitRestActionCompletion()
    }
}
