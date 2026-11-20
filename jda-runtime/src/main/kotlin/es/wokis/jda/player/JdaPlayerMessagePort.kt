package es.wokis.jda.player

import es.wokis.domain.model.ChannelId
import es.wokis.jda.awaitRestAction
import es.wokis.jda.awaitRestActionCompletion
import es.wokis.message.MessageView
import es.wokis.player.PlayerMessageId
import es.wokis.player.PlayerMessagePermanentFailure
import es.wokis.player.PlayerMessagePort
import kotlinx.coroutines.CancellationException
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.exceptions.ErrorResponseException
import net.dv8tion.jda.api.exceptions.MissingAccessException
import net.dv8tion.jda.api.exceptions.ObfuscatedChannelException
import net.dv8tion.jda.api.requests.ErrorResponse
import org.slf4j.LoggerFactory

/** [PlayerMessagePort] backed by JDA; plain text keeps the single-content REST path. */
internal class JdaPlayerMessagePort(
    private val jda: JDA,
) : PlayerMessagePort {

    override suspend fun send(channelId: ChannelId, view: MessageView): PlayerMessageId {
        val channel = requireTextChannel(channelId)
        val message = if (view.isTextOnly) {
            channel.sendMessage(requireText(view)).awaitRestAction()
        } else {
            channel.sendMessage(JdaMessageViewMapper.toCreateData(view)).awaitRestAction()
        }
        return PlayerMessageId(message.idLong.toULong())
    }

    override suspend fun edit(channelId: ChannelId, messageId: PlayerMessageId, view: MessageView) {
        try {
            // A channel the bot cannot resolve can never serve this edit, so it reports the same
            // permanent loss as UNKNOWN_CHANNEL instead of a doomed retry on the same handle.
            val channel = requireTextChannel(channelId) { message -> PlayerMessagePermanentFailure(message) }
            if (view.isTextOnly) {
                channel.editMessageById(messageId.toJdaId(), requireText(view))
            } else {
                channel.editMessageById(messageId.toJdaId(), JdaMessageViewMapper.toEditData(view))
            }.awaitRestActionCompletion()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (permanent: PlayerMessagePermanentFailure) {
            throw permanent
        } catch (error: Exception) {
            // A retryable failure (rate limit, timeout, transient 5xx) keeps its original type so the
            // caller can retry with the same message handle.
            throw if (error.isPermanentEditFailure()) {
                PlayerMessagePermanentFailure(PERMANENT_EDIT_FAILURE, error)
            } else {
                error
            }
        }
    }

    override suspend fun delete(channelId: ChannelId, messageId: PlayerMessageId) {
        requireTextChannel(channelId)
            .deleteMessageById(messageId.toJdaId())
            .awaitRestActionCompletion()
    }

    /**
     * Resolves [channelId] or throws the failure built by [missingChannel] from
     * [MISSING_TEXT_CHANNEL].
     *
     * [edit] treats an unresolvable channel as permanent, so its caller drops the stale handle;
     * [send] and [delete] keep the plain [IllegalStateException].
     */
    private fun requireTextChannel(
        channelId: ChannelId,
        missingChannel: (String) -> Throwable = { IllegalStateException(it) },
    ): TextChannel {
        val channel = jda.getTextChannelById(channelId.toJdaId()) ?: throw missingChannel(MISSING_TEXT_CHANNEL)
        if (channel.isObfuscated) {
            LOG.warn(
                "A player text channel is hidden from the bot ({})",
                ObfuscatedChannelException::class.simpleName,
            )
            throw ObfuscatedChannelException(channel)
        }
        return channel
    }

    private fun requireText(view: MessageView): String =
        requireNotNull(view.content?.takeIf(String::isNotBlank)) {
            "A text-only player message requires non-blank content"
        }

    private fun Throwable.isPermanentEditFailure(): Boolean = when (this) {
        // Includes ObfuscatedChannelException: the bot cannot see the channel anymore.
        is MissingAccessException -> true
        is ErrorResponseException -> errorResponse in PERMANENT_EDIT_FAILURES
        else -> false
    }

    private companion object {
        private val LOG = LoggerFactory.getLogger(JdaPlayerMessagePort::class.java)

        private const val PERMANENT_EDIT_FAILURE =
            "The player message can no longer be edited; the next /player rebuilds it"

        /** Value-free: an unresolvable channel id must never leak into a log or a user-facing text. */
        private const val MISSING_TEXT_CHANNEL =
            "The requested player text channel is not available to the bot"

        /** Discord codes that can never succeed on retry for the same message. */
        private val PERMANENT_EDIT_FAILURES = setOf(
            ErrorResponse.UNKNOWN_MESSAGE,
            ErrorResponse.UNKNOWN_CHANNEL,
            ErrorResponse.MISSING_ACCESS,
            ErrorResponse.MISSING_PERMISSIONS,
        )
    }
}

private fun ChannelId.toJdaId(): Long = value.toJdaLong("Channel id")

private fun PlayerMessageId.toJdaId(): Long = value.toJdaLong("Message id")

private fun ULong.toJdaLong(label: String): Long {
    require(this <= Long.MAX_VALUE.toULong()) {
        "$label does not fit in JDA's signed 64-bit id range"
    }
    return toLong()
}
