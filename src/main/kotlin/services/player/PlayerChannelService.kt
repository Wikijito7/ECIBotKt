package es.wokis.services.player

import dev.kord.common.entity.Overwrite
import dev.kord.common.entity.OverwriteType
import dev.kord.common.entity.Permission
import dev.kord.common.entity.Permissions
import dev.kord.core.behavior.channel.createMessage
import dev.kord.core.behavior.createTextChannel
import dev.kord.core.entity.Guild
import dev.kord.core.entity.Message
import dev.kord.core.entity.channel.TextChannel
import dev.kord.core.entity.interaction.ApplicationCommandInteraction
import dev.kord.rest.builder.message.create.MessageCreateBuilder
import es.wokis.utils.Log
import kotlinx.coroutines.flow.toList
import services.player.result.PlayerChannelResult
import kotlin.collections.mutableSetOf

private const val TAG = "PlayerChannelService"
private const val PLAYER_CHANNEL_NAME = "player"

private const val MAX_MESSAGES_BULK_DELETE = 90

/**
 * Result data class containing the message, channel, and creation info
 */

class PlayerChannelService {

    /**
     * Sends a player embed message to the dedicated player channel.
     * If the channel doesn't exist, attempts to create it with restricted permissions.
     * All previous messages in the channel will be cleared.
     *
     * @param interaction The command interaction to get guild and locale info
     * @param buildMessage Function to build the message content
     * @return Result containing the PlayerChannelResult or an error
     */
    suspend fun sendPlayerMessage(
        interaction: ApplicationCommandInteraction,
        buildMessage: MessageCreateBuilder.() -> Unit
    ): Result<PlayerChannelResult> {
        return try {
            val guildId = interaction.data.guildId.value
                ?: return Result.failure(IllegalStateException("Guild ID is null"))
            val guild = interaction.kord.getGuildOrNull(guildId)
                ?: return Result.failure(IllegalStateException("Guild not found"))
            val (playerChannel, isNewChannel) = findOrCreatePlayerChannel(guild)
                ?: return Result.failure(IllegalStateException("Failed to find or create #player channel in guild '${guild.name}'"))

            clearChannelMessages(playerChannel)

            playerChannel.createMessage { buildMessage() }.let { message ->
                Result.success(PlayerChannelResult(message, playerChannel, isNewChannel))
            }
        } catch (e: Exception) {
            Log.error("$TAG: Error sending player message: ${e.message}", e)
            Result.failure(e)
        }
    }

    private suspend fun findOrCreatePlayerChannel(guild: Guild): Pair<TextChannel, Boolean>? {
        return try {
            val existingChannel = guild.channels.toList().find { it.name == PLAYER_CHANNEL_NAME } as? TextChannel
            existingChannel?.let {
                Log.info("$TAG: Found existing #player channel in guild '${guild.name}' (ID: ${existingChannel.id})")
                return Pair(existingChannel, false)
            }

            Log.info("$TAG: #player channel not found in guild '${guild.name}', attempting to create...")
            createPlayerChannel(guild)?.let { Pair(it, true) }
        } catch (e: Exception) {
            Log.error("$TAG: Error finding or creating player channel in guild '${guild.name}': ${e.message}", e)
            null
        }
    }

    private suspend fun createPlayerChannel(guild: Guild): TextChannel? {
        val channel = try {
            guild.createTextChannel(PLAYER_CHANNEL_NAME) {
                permissionOverwrites = mutableSetOf(
                    Overwrite(
                        id = guild.id,
                        type = OverwriteType.Role,
                        allow = Permissions(Permission.ViewChannel, Permission.ReadMessageHistory),
                        deny = Permissions(Permission.SendMessages)
                    )
                )
            }.also {
                Log.info("$TAG: Successfully created #player channel '${it.name}' (ID: ${it.id}) in guild '${guild.name}'")
            }
        } catch (e: Exception) {
            Log.error("$TAG: Unexpected error creating #player channel in guild '${guild.name}': ${e.message}", e)
            return null
        }

        return channel
    }

    private suspend fun clearChannelMessages(channel: TextChannel) {
        val messages = channel.messages.toList()
        try {
            if (messages.isEmpty()) {
                return
            }
            messages.map { it.id }.chunked(MAX_MESSAGES_BULK_DELETE).forEach {
                channel.bulkDelete(messages = it)
            }
            Log.info("$TAG: Cleared ${messages.size} messages from player channel")
        } catch (e: Exception) {
            Log.error("$TAG: Error clearing messages from player channel: ${e.message}")
            messages.forEach { it.deleteMessageSafely() }
        }
    }

    private suspend fun Message.deleteMessageSafely() {
        try {
            delete()
        } catch (e: Exception) {
            Log.error("$TAG: Failed to delete message $id: ${e.message}")
        }
    }
}
