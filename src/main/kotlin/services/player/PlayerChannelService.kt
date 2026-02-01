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
import es.wokis.services.localization.LocalizationService
import es.wokis.utils.Log
import kotlinx.coroutines.flow.toList
import kotlin.collections.mutableSetOf

private const val TAG = "PlayerChannelService"
private const val PLAYER_CHANNEL_NAME = "player"

/**
 * Result data class containing the message, channel, and creation info
 */
data class PlayerChannelResult(
    val message: Message,
    val channel: TextChannel,
    val isNewChannel: Boolean
)

class PlayerChannelService(
    private val localizationService: LocalizationService
) {

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

            val message = playerChannel.createMessage { buildMessage() }

            Result.success(PlayerChannelResult(message, playerChannel, isNewChannel))
        } catch (e: Exception) {
            Log.error("$TAG: Error sending player message: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Finds an existing player channel or creates a new one.
     * Returns the channel and a boolean indicating if it was newly created.
     */
    private suspend fun findOrCreatePlayerChannel(guild: Guild): Pair<TextChannel, Boolean>? {
        return try {
            // First try to find existing channel
            val existingChannel = guild.channels.toList().find { it.name == PLAYER_CHANNEL_NAME } as? TextChannel
            if (existingChannel != null) {
                Log.info("$TAG: Found existing #player channel in guild '${guild.name}' (ID: ${existingChannel.id})")
                return Pair(existingChannel, false)
            }

            // Channel doesn't exist, try to create it
            Log.info("$TAG: #player channel not found in guild '${guild.name}', attempting to create...")
            val newChannel = createPlayerChannel(guild)
            newChannel?.let { Pair(it, true) }
        } catch (e: Exception) {
            Log.error("$TAG: Error finding or creating player channel in guild '${guild.name}': ${e.message}", e)
            null
        }
    }

    /**
     * Creates a new player channel with restricted permissions.
     * Everyone can view but only admins and the bot can write.
     */
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
        } catch (e: dev.kord.rest.request.RestRequestException) {
            // Handle specific Discord REST errors (e.g., missing permissions)
            val errorMessage = when {
                e.message?.contains("50013") == true || e.message?.contains("Missing Permissions") == true ->
                    "Bot lacks 'Manage Channels' permission to create channels"
                e.message?.contains("50001") == true || e.message?.contains("Missing Access") == true ->
                    "Bot lacks access to create channels in this guild"
                e.message?.contains("30007") == true ->
                    "Maximum number of channels reached (500) in this guild"
                e.message?.contains("30013") == true ->
                    "Maximum number of guilds reached for this bot"
                else -> "Discord REST API error: ${e.message}"
            }
            Log.error("$TAG: Failed to create #player channel in guild '${guild.name}': $errorMessage", e)
            return null
        } catch (e: Exception) {
            Log.error("$TAG: Unexpected error creating #player channel in guild '${guild.name}': ${e.message}", e)
            return null
        }

        return channel
    }

    /**
     * Clears ALL messages from the given channel.
     * Since users can't write in the player channel, all messages are deleted.
     */
    private suspend fun clearChannelMessages(channel: TextChannel) {
        try {
            val messages = channel.messages.toList()
            if (messages.isEmpty()) {
                return
            }

            // Delete all messages individually
            messages.forEach { deleteMessageSafely(it) }

            Log.info("$TAG: Cleared ${messages.size} messages from player channel")
        } catch (e: Exception) {
            Log.error("$TAG: Error clearing messages from player channel: ${e.message}")
        }
    }

    private suspend fun deleteMessageSafely(message: Message) {
        try {
            message.delete()
        } catch (e: Exception) {
            Log.error("$TAG: Failed to delete message ${message.id}: ${e.message}")
        }
    }
}
