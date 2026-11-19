package es.wokis.jda.command

import es.wokis.jda.awaitRestActionCompletion
import es.wokis.jda.player.JdaMessageViewMapper
import es.wokis.message.MessageView
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent

/**
 * Narrow seam around the REST operations a responder needs.
 *
 * Keeping the operations behind a delegate lets the responder state machine be exercised
 * without mocking JDA internals, and keeps every JDA type in this adapter.
 *
 * The view operations are optional: only the gateway-backed delegate renders composed views, so a
 * text-only delegate keeps compiling and fails loudly if a view response ever reaches it.
 */
internal interface InteractionResponseDelegate {

    suspend fun defer(ephemeral: Boolean)

    suspend fun reply(content: String, ephemeral: Boolean)

    suspend fun editOriginal(content: String)

    suspend fun reply(view: MessageView, ephemeral: Boolean) {
        throw UnsupportedOperationException(VIEW_RESPONSE_UNSUPPORTED)
    }

    suspend fun editOriginal(view: MessageView) {
        throw UnsupportedOperationException(VIEW_RESPONSE_UNSUPPORTED)
    }

    companion object {
        private const val VIEW_RESPONSE_UNSUPPORTED = "This delegate cannot render composed views"
    }
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

    override suspend fun reply(view: MessageView, ephemeral: Boolean) {
        event.reply(JdaMessageViewMapper.toCreateData(view))
            .setEphemeral(ephemeral)
            .awaitRestActionCompletion()
    }

    override suspend fun editOriginal(view: MessageView) {
        event.hook.editOriginal(JdaMessageViewMapper.toEditData(view)).awaitRestActionCompletion()
    }
}
