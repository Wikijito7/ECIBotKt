package es.wokis.jda.player

import es.wokis.domain.model.ChannelId
import es.wokis.jda.awaitRestAction
import es.wokis.jda.awaitRestActionCompletion
import es.wokis.message.MessageView
import es.wokis.player.PlayerMessageId
import kotlinx.coroutines.CancellationException
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import org.slf4j.LoggerFactory
import java.util.EnumSet

private const val PLAYER_CHANNEL_NAME = "player"
private const val BULK_DELETE_CHUNK_SIZE = 90
private const val HISTORY_PAGE_SIZE = 100

/** Where the player message lives: the channel, its message id and whether the channel is new. */
internal data class JdaPlayerChannelResult(
    val channelId: ChannelId,
    val messageId: PlayerMessageId,
    val isNewChannel: Boolean,
)

/**
 * Kord parity for the dedicated `#player` channel: reuse a case-insensitive match, otherwise create
 * it with an `@everyone` overwrite that only allows viewing and reading history, then clear the
 * backlog and post the player message.
 *
 * Owner-parity note: the overwrite is applied atomically on creation (Kord's behavior) and a failed
 * bulk delete falls back to per-message deletes, because a single foreign/old message aborts the
 * whole bulk request.
 */
internal class JdaPlayerChannelService {

    /** @return the created message, or `null` when the channel could not be found or created. */
    suspend fun setUp(guild: Guild, view: MessageView): JdaPlayerChannelResult? {
        val (channel, isNewChannel) = findOrCreate(guild) ?: return null
        clearMessages(channel)
        val message = try {
            channel.sendMessage(JdaMessageViewMapper.toCreateData(view)).awaitRestAction()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            LOG.warn("Could not send the player message in guild '{}'", guild.name, error)
            return null
        }
        return JdaPlayerChannelResult(
            channelId = ChannelId(channel.idLong.toULong()),
            messageId = PlayerMessageId(message.idLong.toULong()),
            isNewChannel = isNewChannel,
        )
    }

    private suspend fun findOrCreate(guild: Guild): Pair<TextChannel, Boolean>? {
        // Divergence from Kord: the match ignores case, so a renamed `#Player` still counts as the player channel.
        guild.textChannels
            .firstOrNull { it.name.equals(PLAYER_CHANNEL_NAME, ignoreCase = true) }
            ?.let { return it to false }
        return try {
            createChannel(guild) to true
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            LOG.warn("Could not create the #player channel in guild '{}'", guild.name, error)
            null
        }
    }

    private suspend fun createChannel(guild: Guild): TextChannel =
        guild.createTextChannel(PLAYER_CHANNEL_NAME)
            .addPermissionOverride(
                guild.publicRole,
                EnumSet.of(Permission.VIEW_CHANNEL, Permission.MESSAGE_HISTORY),
                EnumSet.of(Permission.MESSAGE_SEND),
            )
            .awaitRestAction()

    /** Kord parity: unbounded backlog clearing; a short page is the only stop condition. */
    private suspend fun clearMessages(channel: TextChannel) {
        var previousPageIds: Set<String> = emptySet()
        try {
            while (true) {
                val page = channel.history.retrievePast(HISTORY_PAGE_SIZE).awaitRestAction()
                if (page.isEmpty()) return
                val pageIds = page.map(Message::getId).toSet()
                // An unchanged full page means nothing could be deleted: stop instead of retrying forever.
                if (pageIds == previousPageIds) return
                deleteMessages(channel, page)
                if (page.size < HISTORY_PAGE_SIZE) return
                previousPageIds = pageIds
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            LOG.warn("Could not clear the #player backlog in guild '{}'", channel.guild.name, error)
        }
    }

    private suspend fun deleteMessages(channel: TextChannel, messages: List<Message>) {
        messages.map(Message::getId).chunked(BULK_DELETE_CHUNK_SIZE).forEach { chunk ->
            try {
                channel.deleteMessagesByIds(chunk).awaitRestActionCompletion()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                LOG.warn("Bulk delete failed in #player; deleting {} messages one by one", chunk.size, error)
                chunk.forEach { deleteMessage(channel, it) }
            }
        }
    }

    private suspend fun deleteMessage(channel: TextChannel, messageId: String) {
        try {
            channel.deleteMessageById(messageId).awaitRestActionCompletion()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            LOG.warn("Could not delete message {} from #player", messageId, error)
        }
    }

    private companion object {
        private val LOG = LoggerFactory.getLogger(JdaPlayerChannelService::class.java)
    }
}
